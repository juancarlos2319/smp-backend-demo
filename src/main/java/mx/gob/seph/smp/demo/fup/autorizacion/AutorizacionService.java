package mx.gob.seph.smp.demo.fup.autorizacion;

import java.time.Duration;
import java.time.LocalDateTime;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import mx.gob.seph.smp.demo.api.ReglaNegocioException;
import mx.gob.seph.smp.demo.fup.comun.FupRepository;
import mx.gob.seph.smp.demo.fup.comun.FupRepository.Etapa;
import mx.gob.seph.smp.demo.fup.comun.FupRepository.Folio;
import mx.gob.seph.smp.demo.fup.comun.FupRepository.MovimientoFolio;
import mx.gob.seph.smp.demo.fup.comun.FupRepository.MovimientoInterno;
import mx.gob.seph.smp.demo.fup.comun.Validaciones;
import mx.gob.seph.smp.demo.seguridad.Permiso;
import mx.gob.seph.smp.demo.seguridad.PermisosService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Autorización de movimientos (MOV_AUTORIZACION): cada folio pasa por las 10 etapas de `fup_etapas`. Firmar
 * registro cierra la captura (A → F); firmar pagos procesa el folio (F → P), ocupa las plazas y activa al empleado.
 */
@Service
public class AutorizacionService {
    public static final String MODULO = "MOV_AUTORIZACION";
    private static final List<String> ETAPAS_RECHAZO = List.of("informatica_validar", "informatica");
    private static final int LIMIT_MAX = 200;
    private static final int OBSERVACION_MAXIMO = 255;

    public record EtapaAutorizacion(String etapa, boolean opcional, int pendientes) {
    }

    public record EtapasAutorizacion(List<EtapaAutorizacion> etapas, boolean puedeRechazar) {
    }

    public record FolioResumen(
            int acuerdo, String status, boolean rechazado, String tsCaptura, Integer nivelId, String nivel,
            Integer empleadoId, String rfc, String nombreCompleto, String plazas, String movimientos,
            String enEtapaDesde, Long diasEnEtapa, boolean puedeAprobar) {
    }

    public record Pendientes(int total, List<FolioResumen> folios) {
    }

    public record Firma(String etapa, boolean opcional, boolean firmada, String fecha, boolean puedeAprobar) {
    }

    public record Rechazo(String fecha, Integer motivoId, String motivo, String observacion, String usuario) {
    }

    public record Detalle(
            FolioResumen folio, List<MovimientoFolio> movimientos, List<Firma> firmas, boolean puedeRechazar,
            List<Rechazo> rechazos) {
    }

    public record MotivoRechazo(int id, String descripcion) {
    }

    public record RechazoRequest(Integer catRechazosId, String observacion) {
    }

    private final JdbcClient jdbc;
    private final FupRepository fup;
    private final PermisosService permisos;

    public AutorizacionService(JdbcClient jdbc, FupRepository fup, PermisosService permisos) {
        this.jdbc = jdbc;
        this.fup = fup;
        this.permisos = permisos;
    }

    public EtapasAutorizacion etapas(long userId) {
        List<String> delUsuario = permisos.etapas(userId);
        List<Etapa> todas = fup.etapas();
        List<FolioEstado> estados = estados();
        List<EtapaAutorizacion> etapas = todas.stream()
                .filter(e -> delUsuario.contains(e.etapa()))
                .map(e -> new EtapaAutorizacion(e.etapa(), e.opcional(),
                        (int) estados.stream().filter(s -> s.pendientes().contains(e.etapa())).count()))
                .toList();
        return new EtapasAutorizacion(etapas, puedeRechazar(delUsuario));
    }

    public Pendientes pendientes(
            long userId, String etapa, String acuerdoParam, String rfcParam, String plazaParam, Integer limit,
            Integer offset) {
        if (!permisos.etapas(userId).contains(etapa)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu rol no tiene la etapa '" + etapa + "'.");
        }
        Integer acuerdo = null;
        if (acuerdoParam != null && !acuerdoParam.isBlank()) {
            if (!acuerdoParam.trim().matches("\\d{1,9}")) {
                throw new ReglaNegocioException("El acuerdo debe ser un número de hasta 9 dígitos.");
            }
            acuerdo = Integer.valueOf(acuerdoParam.trim());
        }
        String rfc = Validaciones.filtroTexto(rfcParam, 4, Validaciones.TEXTO_RFC, "RFC");
        String plaza = Validaciones.filtroTexto(plazaParam, 4, Validaciones.TEXTO_PLAZA, "texto de la clave de plaza");
        int max = limit == null || limit < 1 ? 50 : Math.min(limit, LIMIT_MAX);
        int desde = offset == null || offset < 0 ? 0 : offset;

        Integer acuerdoFiltro = acuerdo;
        List<FolioResumen> filtrados = estados().stream()
                .filter(s -> s.pendientes().contains(etapa))
                .filter(s -> acuerdoFiltro == null || s.folio().acuerdo() == acuerdoFiltro)
                .filter(s -> rfc == null || (s.folio().rfc() != null && s.folio().rfc().toUpperCase().startsWith(rfc)))
                .filter(s -> plaza == null || s.movimientos().stream().anyMatch(
                        m -> m.movimiento().cvePlaza() != null && m.movimiento().cvePlaza().toUpperCase().contains(plaza)))
                .map(s -> resumen(userId, s, enEtapaDesde(s, etapa)))
                .sorted(Comparator.comparing((FolioResumen r) -> r.diasEnEtapa() == null)
                        .thenComparing(FolioResumen::enEtapaDesde))
                .toList();
        int hasta = Math.min(filtrados.size(), desde + max);
        return new Pendientes(filtrados.size(), desde >= hasta ? List.of() : filtrados.subList(desde, hasta));
    }

    public Detalle detalle(long userId, int acuerdo) {
        FolioEstado estado = estado(fup.folioOr404(acuerdo));
        List<String> delUsuario = permisos.etapas(userId);
        boolean cubre = cubreTodos(userId, estado.movimientos());
        boolean abierto = !"P".equals(estado.folio().status());
        List<Firma> firmas = fup.etapas().stream()
                .map(e -> {
                    LocalDateTime fecha = estado.firmas().get(e.etapa());
                    boolean puede = abierto && cubre && estado.pendientes().contains(e.etapa())
                            && delUsuario.contains(e.etapa());
                    return new Firma(e.etapa(), e.opcional(), fecha != null, fecha == null ? null : fecha.toString(),
                            puede);
                })
                .toList();
        FolioResumen resumen = resumen(userId, estado, estado.folio().tsCaptura());
        resumen = new FolioResumen(resumen.acuerdo(), resumen.status(), resumen.rechazado(), resumen.tsCaptura(),
                resumen.nivelId(), resumen.nivel(), resumen.empleadoId(), resumen.rfc(), resumen.nombreCompleto(),
                resumen.plazas(), resumen.movimientos(), resumen.enEtapaDesde(), resumen.diasEnEtapa(),
                firmas.stream().anyMatch(Firma::puedeAprobar));
        return new Detalle(resumen, estado.movimientos().stream().map(MovimientoInterno::movimiento).toList(), firmas,
                abierto && puedeRechazar(delUsuario), rechazos(acuerdo));
    }

    public List<MotivoRechazo> motivosRechazo() {
        return jdbc.sql("SELECT id, descripcion FROM cat_rechazos ORDER BY id")
                .query((row, n) -> new MotivoRechazo(row.getInt("id"), row.getString("descripcion"))).list();
    }

    @Transactional
    public String aprobar(long userId, int acuerdo, String etapa) {
        Folio folio = fup.folioOr404(acuerdo);
        List<Etapa> etapas = fup.etapas();
        if (etapas.stream().noneMatch(e -> e.etapa().equals(etapa))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la etapa '" + etapa + "'.");
        }
        if ("P".equals(folio.status())) {
            throw new ReglaNegocioException("El folio " + acuerdo + " ya se procesó.");
        }
        if (!permisos.etapas(userId).contains(etapa)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu rol no tiene la etapa '" + etapa + "'.");
        }
        List<MovimientoInterno> movs = fup.movimientos(acuerdo);
        if (!cubreTodos(userId, movs)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Necesitas AUTORIZAR en todos los movimientos del folio.");
        }
        Map<String, LocalDateTime> firmas = fup.firmas(acuerdo);
        if (firmas.containsKey(etapa)) {
            throw new ReglaNegocioException("La etapa '" + etapa + "' del folio " + acuerdo + " ya está firmada.");
        }
        Optional<String> falta = FupRepository.faltaAntes(etapas, firmas, etapa);
        if (falta.isPresent()) {
            throw new ReglaNegocioException("Falta firmar la etapa '" + falta.get() + "'.");
        }
        boolean opcional = etapas.stream().anyMatch(e -> e.etapa().equals(etapa) && e.opcional());
        if (opcional && !firmas.containsKey("registro")) {
            throw new ReglaNegocioException("Falta firmar la etapa 'registro'.");
        }

        jdbc.sql("INSERT INTO fup_firmas (acuerdo, etapa, fecha, user_id) VALUES (:acuerdo, :etapa, now(), :user)")
                .param("acuerdo", acuerdo).param("etapa", etapa).param("user", userId).update();
        if ("registro".equals(etapa)) {
            jdbc.sql("UPDATE fup_folios SET status = 'F' WHERE acuerdo = :acuerdo").param("acuerdo", acuerdo).update();
        }
        if ("pagos".equals(etapa)) {
            procesar(folio, movs);
            return "Se aprobó la etapa Pagos: el folio " + acuerdo + " quedó procesado y el empleado activo.";
        }
        return null;
    }

    @Transactional
    public void rechazar(long userId, int acuerdo, RechazoRequest body) {
        Folio folio = fup.folioOr404(acuerdo);
        if (!puedeRechazar(permisos.etapas(userId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo Informática puede rechazar folios.");
        }
        if ("P".equals(folio.status())) {
            throw new ReglaNegocioException("El folio " + acuerdo + " ya se procesó.");
        }
        if (body == null || body.catRechazosId() == null || jdbc.sql("SELECT count(*) FROM cat_rechazos WHERE id = :id")
                .param("id", body.catRechazosId()).query(Integer.class).single() == 0) {
            throw new ReglaNegocioException("Elige un motivo de rechazo válido.");
        }
        String observacion = Validaciones.blankToNull(body.observacion());
        if (observacion != null && observacion.length() > OBSERVACION_MAXIMO) {
            throw new ReglaNegocioException("La observación pasa de " + OBSERVACION_MAXIMO + " caracteres.");
        }
        jdbc.sql("DELETE FROM fup_firmas WHERE acuerdo = :acuerdo").param("acuerdo", acuerdo).update();
        jdbc.sql("UPDATE fup_folios SET status = 'A', rechazado = TRUE WHERE acuerdo = :acuerdo")
                .param("acuerdo", acuerdo).update();
        jdbc.sql("""
                        INSERT INTO fup_rechazos (acuerdo, fecha, cat_rechazos_id, observacion, user_id)
                        VALUES (:acuerdo, now(), :motivo, :observacion, :user)
                        """)
                .param("acuerdo", acuerdo).param("motivo", body.catRechazosId()).param("observacion", observacion)
                .param("user", userId).update();
    }

    // Estado de los folios

    private record FolioEstado(
            Folio folio, List<MovimientoInterno> movimientos, Map<String, LocalDateTime> firmas,
            List<String> pendientes) {
    }

    private List<FolioEstado> estados() {
        return fup.foliosSinProcesar().stream().map(this::estado).toList();
    }

    private FolioEstado estado(Folio folio) {
        Map<String, LocalDateTime> firmas = fup.firmas(folio.acuerdo());
        List<String> pendientes = "P".equals(folio.status())
                ? List.of()
                : FupRepository.etapasPendientes(fup.etapas(), firmas);
        return new FolioEstado(folio, fup.movimientos(folio.acuerdo()), firmas, pendientes);
    }

    /** Desde cuándo espera en la etapa: la firma anterior más reciente, o la captura / el último rechazo. */
    private LocalDateTime enEtapaDesde(FolioEstado estado, String etapa) {
        LocalDateTime base = estado.folio().tsCaptura();
        if ("registro".equals(etapa)) {
            Optional<LocalDateTime> rechazo = fup.ultimoRechazo(estado.folio().acuerdo());
            return rechazo.filter(r -> r.isAfter(base)).orElse(base);
        }
        return estado.firmas().values().stream().max(Comparator.naturalOrder()).orElse(base);
    }

    private FolioResumen resumen(long userId, FolioEstado estado, LocalDateTime desde) {
        Folio f = estado.folio();
        boolean legacy = f.legacy() || !desde.isAfter(FupRepository.FECHA_LEGACY);
        Long dias = legacy ? null : Duration.between(desde, LocalDateTime.now()).toDays();
        return new FolioResumen(f.acuerdo(), f.status(), f.rechazado(), f.tsCapturaIso(), f.nivelId(), f.nivel(),
                f.empleadoId(), f.rfc(), f.nombreCompleto(), FupRepository.plazasTexto(estado.movimientos()),
                FupRepository.movimientosTexto(estado.movimientos()), desde.toString(), dias,
                !"P".equals(f.status()) && cubreTodos(userId, estado.movimientos()));
    }

    private boolean cubreTodos(long userId, List<MovimientoInterno> movs) {
        return movs.stream().allMatch(m -> permisos.cubre(userId, Permiso.AUTORIZAR,
                m.movimiento().movimiento(), m.movimiento().motivo()));
    }

    private static boolean puedeRechazar(List<String> etapasUsuario) {
        return etapasUsuario.stream().anyMatch(ETAPAS_RECHAZO::contains);
    }

    private List<Rechazo> rechazos(int acuerdo) {
        return jdbc.sql("""
                        SELECT r.fecha, r.cat_rechazos_id, c.descripcion, r.observacion, u.username
                        FROM fup_rechazos r
                        LEFT JOIN cat_rechazos c ON c.id = r.cat_rechazos_id
                        LEFT JOIN core_users u ON u.id = r.user_id
                        WHERE r.acuerdo = :acuerdo
                        ORDER BY r.fecha DESC NULLS LAST, r.id DESC
                        """)
                .param("acuerdo", acuerdo)
                .query((row, n) -> new Rechazo(
                        row.getTimestamp("fecha") == null ? null : row.getTimestamp("fecha").toLocalDateTime().toString(),
                        (Integer) row.getObject("cat_rechazos_id"), row.getString("descripcion"),
                        row.getString("observacion"), row.getString("username")))
                .list();
    }

    /** Pagos: plazas ocupadas por el empleado, empleado activo, folio procesado. */
    private void procesar(Folio folio, List<MovimientoInterno> movs) {
        jdbc.sql("UPDATE fup_folios SET status = 'P' WHERE acuerdo = :acuerdo").param("acuerdo", folio.acuerdo()).update();
        if (folio.empleadoId() == null) {
            return;
        }
        for (MovimientoInterno m : movs) {
            MovimientoFolio mov = m.movimiento();
            fup.actualizarPlaza(mov.plazaId(), FupRepository.PLAZA_ACTIVA, "Activa", mov.centroTrabajo());
            jdbc.sql("""
                            INSERT INTO demo_position_occupants (position_id, employee_id, status_id, status_name,
                                                                 salary_level, employee_position, occupant)
                            SELECT p.id, e.id, 1, 'Activa', 1,
                                   jsonb_build_object('plazaId', p.id, 'cvePlaza', p.code, 'categoria', p.category_code,
                                       'categoriaDescripcion', c.description, 'horas', p.hours,
                                       'centroTrabajo', p.center_code, 'estatusId', 1, 'estatus', 'Activa',
                                       'nivelSueldo', 1),
                                   jsonb_build_object('empleadoId', e.id, 'rfc', e.rfc, 'nombreCompleto', e.full_name,
                                       'estatusId', 1, 'estatus', 'Activa', 'nivelSueldo', 1)
                            FROM demo_positions p
                            JOIN demo_categories c ON c.code = p.category_code
                            JOIN demo_employees e ON e.id = :empleado
                            WHERE p.id = :plaza
                            ON CONFLICT (position_id, employee_id) DO NOTHING
                            """)
                    .param("empleado", folio.empleadoId()).param("plaza", mov.plazaId()).update();
        }
        jdbc.sql("""
                        UPDATE demo_employees SET active = TRUE, details = details || jsonb_build_object('activo', TRUE)
                        WHERE id = :id
                        """)
                .param("id", folio.empleadoId()).update();
    }
}
