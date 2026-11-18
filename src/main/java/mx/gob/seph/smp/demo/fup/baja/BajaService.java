package mx.gob.seph.smp.demo.fup.baja;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import mx.gob.seph.smp.demo.api.ReglaNegocioException;
import mx.gob.seph.smp.demo.fup.comun.FupRepository;
import mx.gob.seph.smp.demo.fup.comun.FupRepository.Folio;
import mx.gob.seph.smp.demo.fup.comun.FupRepository.MovimientoFolio;
import mx.gob.seph.smp.demo.fup.comun.FupRepository.MovimientoInterno;
import mx.gob.seph.smp.demo.fup.comun.Validaciones;
import mx.gob.seph.smp.demo.seguridad.Permiso;
import mx.gob.seph.smp.demo.seguridad.PermisosService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Baja de movimientos (MOV_BAJA_MOVIMIENTOS): borra un folio sin procesar (borrado lógico) y deshace su
 * captura con la foto previa de cada plaza. Si el folio creó al empleado, también lo borra.
 */
@Service
public class BajaService {
    public static final String MODULO = "MOV_BAJA_MOVIMIENTOS";
    private static final int LIMIT_MAX = 50;

    public record FolioBajaResumen(
            int acuerdo, String status, boolean rechazado, String tsCaptura, Integer empleadoId, String rfc,
            String nombreCompleto, String plazas, String movimientos, boolean conFotoPrevia) {
    }

    public record FolioBaja(
            int acuerdo, String status, boolean rechazado, String tsCaptura, Integer empleadoId, String rfc,
            String nombreCompleto, List<MovimientoFolio> movimientos, boolean borraEmpleado, boolean puedeDarDeBaja,
            List<String> impedimentos) {
    }

    public record BajaFolioResultado(int acuerdo, int movimientos, int plazasRestauradas, boolean empleadoBorrado) {
    }

    private final JdbcClient jdbc;
    private final FupRepository fup;
    private final PermisosService permisos;

    public BajaService(JdbcClient jdbc, FupRepository fup, PermisosService permisos) {
        this.jdbc = jdbc;
        this.fup = fup;
        this.permisos = permisos;
    }

    /** Folios sin procesar por inicio del RFC y/o texto de la clave de plaza; recientes primero, legacy al final. */
    public List<FolioBajaResumen> buscar(String rfcParam, String plazaParam, Integer limit) {
        String rfc = Validaciones.filtroTexto(rfcParam, 4, Validaciones.TEXTO_RFC, "RFC");
        String plaza = Validaciones.filtroTexto(plazaParam, 4, Validaciones.TEXTO_PLAZA, "texto de la clave de plaza");
        if (rfc == null && plaza == null) {
            throw new ReglaNegocioException("Escribe el RFC del empleado o la clave de la plaza.");
        }
        int max = limit == null || limit < 1 ? 20 : Math.min(limit, LIMIT_MAX);
        List<FolioBajaResumen> result = new ArrayList<>();
        fup.foliosSinProcesar().stream()
                .filter(f -> rfc == null || (f.rfc() != null && f.rfc().toUpperCase().startsWith(rfc)))
                .sorted(Comparator.comparing(Folio::legacy).thenComparing(Folio::tsCaptura, Comparator.reverseOrder()))
                .forEach(f -> {
                    if (result.size() >= max) {
                        return;
                    }
                    List<MovimientoInterno> movs = fup.movimientos(f.acuerdo());
                    if (plaza != null && movs.stream().noneMatch(
                            m -> m.movimiento().cvePlaza() != null && m.movimiento().cvePlaza().toUpperCase().contains(plaza))) {
                        return;
                    }
                    result.add(new FolioBajaResumen(f.acuerdo(), f.status(), f.rechazado(), f.tsCapturaIso(),
                            f.empleadoId(), f.rfc(), f.nombreCompleto(), FupRepository.plazasTexto(movs),
                            FupRepository.movimientosTexto(movs), movs.stream().allMatch(MovimientoInterno::conFoto)));
                });
        return result;
    }

    public FolioBaja folio(long userId, int acuerdo) {
        Folio f = fup.folioOr404(acuerdo);
        List<MovimientoInterno> movs = fup.movimientos(acuerdo);
        List<String> impedimentos = impedimentos(userId, f, movs);
        return new FolioBaja(f.acuerdo(), f.status(), f.rechazado(), f.tsCapturaIso(), f.empleadoId(), f.rfc(),
                f.nombreCompleto(), movs.stream().map(MovimientoInterno::movimiento).toList(), f.creaEmpleado(),
                impedimentos.isEmpty(), impedimentos);
    }

    @Transactional
    public BajaFolioResultado darDeBaja(long userId, int acuerdo) {
        Folio f = fup.folioOr404(acuerdo);
        List<MovimientoInterno> movs = fup.movimientos(acuerdo);
        List<String> impedimentos = impedimentos(userId, f, movs);
        if (!impedimentos.isEmpty()) {
            throw new ReglaNegocioException(String.join(" ", impedimentos));
        }
        int restauradas = 0;
        for (MovimientoInterno m : movs) {
            fup.actualizarPlaza(m.movimiento().plazaId(), m.prevStatusId(), m.prevStatusName(), m.prevCenterCode());
            restauradas++;
        }
        jdbc.sql("UPDATE fup_folios SET deleted = TRUE WHERE acuerdo = :acuerdo").param("acuerdo", acuerdo).update();
        jdbc.sql("DELETE FROM fup_firmas WHERE acuerdo = :acuerdo").param("acuerdo", acuerdo).update();
        boolean empleadoBorrado = false;
        if (f.creaEmpleado() && f.empleadoId() != null) {
            // fup_empleados y fup_domicilios se van en cascada
            jdbc.sql("DELETE FROM demo_employees WHERE id = :id").param("id", f.empleadoId()).update();
            empleadoBorrado = true;
        }
        return new BajaFolioResultado(acuerdo, movs.size(), restauradas, empleadoBorrado);
    }

    /** Textos listos para mostrar; vacío si se puede dar de baja. */
    private List<String> impedimentos(long userId, Folio folio, List<MovimientoInterno> movs) {
        List<String> impedimentos = new ArrayList<>();
        if ("P".equals(folio.status())) {
            impedimentos.add("El folio ya se procesó en Pagos.");
        }
        if (movs.stream().anyMatch(m -> !m.conFoto())) {
            impedimentos.add("El folio no tiene foto previa de sus plazas (es del legacy o se capturó antes de la baja).");
        }
        if (!permisos.etapas(userId).contains("registro")) {
            impedimentos.add("Tu rol no tiene la etapa de registro.");
        }
        movs.stream()
                .map(m -> m.movimiento().movimiento() + "/" + m.movimiento().motivo())
                .distinct()
                .filter(clave -> !permisos.cubre(userId, Permiso.ELIMINAR, clave.substring(0, 2), clave.substring(3)))
                .forEach(clave -> impedimentos.add("No tienes ELIMINAR en el movimiento " + clave + "."));
        return impedimentos;
    }
}
