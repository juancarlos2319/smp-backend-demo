package mx.gob.seph.smp.demo.fup.comun;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

/** Lecturas y escrituras comunes de los folios FUP (captura, baja y autorización). */
@Repository
public class FupRepository {
    /** Los folios del legacy traen esta fecha de captura. */
    public static final LocalDateTime FECHA_LEGACY = LocalDateTime.of(1900, 1, 1, 0, 0);
    public static final int QNA_SIN_TERMINO = 999999;

    public static final int PLAZA_ACTIVA = 1;
    public static final int PLAZA_VACANTE = 2;
    public static final int PLAZA_EN_TRAMITE = 3;

    public record Folio(
            int acuerdo, String status, boolean rechazado, LocalDateTime tsCaptura, Integer nivelId, String nivel,
            Integer empleadoId, String rfc, String nombreCompleto, boolean creaEmpleado) {

        public boolean legacy() {
            return !tsCaptura.isAfter(FECHA_LEGACY);
        }

        /** ISO LocalDateTime, como lo espera el front (`1900-01-01...` en los del legacy). */
        public String tsCapturaIso() {
            return tsCaptura.toString();
        }
    }

    /** Movimiento tal como lo pinta el front (`MovimientoFolioBaja`). */
    public record MovimientoFolio(
            int id, int plazaId, String cvePlaza, String movimiento, String movimientoDescripcion, String motivo,
            String motivoDescripcion, Integer qnaIni, Integer qnaFin, String centroTrabajo) {
    }

    /** Movimiento con su foto previa, para la baja. */
    public record MovimientoInterno(
            MovimientoFolio movimiento, boolean conFoto, Integer prevStatusId, String prevStatusName,
            String prevCenterCode) {
    }

    public record Etapa(String etapa, int orden, boolean opcional) {
    }

    private final JdbcClient jdbc;

    public FupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Folio> folio(int acuerdo) {
        return jdbc.sql(FOLIO_SELECT + " WHERE f.acuerdo = :acuerdo AND NOT f.deleted")
                .param("acuerdo", acuerdo).query(FupRepository::mapFolio).optional();
    }

    public Folio folioOr404(int acuerdo) {
        return folio(acuerdo).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el folio " + acuerdo + "."));
    }

    /** Folios sin procesar (A o F). */
    public List<Folio> foliosSinProcesar() {
        return jdbc.sql(FOLIO_SELECT + " WHERE f.status IN ('A', 'F') AND NOT f.deleted ORDER BY f.acuerdo")
                .query(FupRepository::mapFolio).list();
    }

    public List<MovimientoInterno> movimientos(int acuerdo) {
        return jdbc.sql("""
                        SELECT fm.id, fm.tab_plaza_id, p.code AS cve_plaza, fm.cve_movimiento, cm.descripcion AS mov_desc,
                               fm.cve_motivo, mo.descripcion AS motivo_desc, fm.qna_ini, fm.qna_fin, fm.cct_clave,
                               fm.con_foto, fm.prev_status_id, fm.prev_status_name, fm.prev_center_code
                        FROM fup_movimientos fm
                        JOIN demo_positions p ON p.id = fm.tab_plaza_id
                        LEFT JOIN cat_movimientos cm ON cm.cve = fm.cve_movimiento
                        LEFT JOIN cat_motivos mo ON mo.cve_movimiento = fm.cve_movimiento AND mo.cve_motivo = fm.cve_motivo
                        WHERE fm.acuerdo = :acuerdo
                        ORDER BY fm.orden
                        """)
                .param("acuerdo", acuerdo)
                .query((row, n) -> new MovimientoInterno(
                        new MovimientoFolio(row.getInt("id"), row.getInt("tab_plaza_id"), row.getString("cve_plaza"),
                                row.getString("cve_movimiento"), row.getString("mov_desc"), row.getString("cve_motivo"),
                                row.getString("motivo_desc"), row.getInt("qna_ini"), row.getInt("qna_fin"),
                                row.getString("cct_clave")),
                        row.getBoolean("con_foto"), (Integer) row.getObject("prev_status_id"),
                        row.getString("prev_status_name"), row.getString("prev_center_code")))
                .list();
    }

    /** Movimientos de varios folios, agrupados por acuerdo (para las listas). */
    public Map<Integer, List<MovimientoInterno>> movimientos(List<Integer> acuerdos) {
        Map<Integer, List<MovimientoInterno>> result = new LinkedHashMap<>();
        for (Integer acuerdo : acuerdos) {
            result.put(acuerdo, movimientos(acuerdo));
        }
        return result;
    }

    /** Etapas firmadas del folio con su fecha. */
    public Map<String, LocalDateTime> firmas(int acuerdo) {
        Map<String, LocalDateTime> firmas = new LinkedHashMap<>();
        jdbc.sql("SELECT etapa, fecha FROM fup_firmas WHERE acuerdo = :acuerdo")
                .param("acuerdo", acuerdo)
                .query((ResultSet row) -> {
                    firmas.put(row.getString("etapa"), row.getTimestamp("fecha").toLocalDateTime());
                });
        return firmas;
    }

    /** Fecha del último rechazo, para saber desde cuándo espera en registro. */
    public Optional<LocalDateTime> ultimoRechazo(int acuerdo) {
        return jdbc.sql("""
                        SELECT fecha FROM fup_rechazos
                        WHERE acuerdo = :acuerdo AND fecha IS NOT NULL
                        ORDER BY fecha DESC LIMIT 1
                        """)
                .param("acuerdo", acuerdo)
                .query((row, n) -> row.getTimestamp("fecha"))
                .optional()
                .map(Timestamp::toLocalDateTime);
    }

    public List<Etapa> etapas() {
        return jdbc.sql("SELECT etapa, orden, opcional FROM fup_etapas ORDER BY orden")
                .query((row, n) -> new Etapa(row.getString("etapa"), row.getInt("orden"), row.getBoolean("opcional")))
                .list();
    }

    /**
     * Etapas que el folio puede firmar ahora: las obligatorias cuando todas las obligatorias anteriores ya
     * están firmadas; las opcionales (SANE, USICAMM) en cuanto se firma registro, sin bloquear a las demás.
     */
    public static List<String> etapasPendientes(List<Etapa> etapas, Map<String, LocalDateTime> firmas) {
        List<String> pendientes = new ArrayList<>();
        boolean obligatoriasCompletas = true;
        boolean registroFirmado = firmas.containsKey("registro");
        for (Etapa etapa : etapas) {
            boolean firmada = firmas.containsKey(etapa.etapa());
            if (etapa.opcional()) {
                if (!firmada && registroFirmado) {
                    pendientes.add(etapa.etapa());
                }
                continue;
            }
            if (!firmada && obligatoriasCompletas) {
                pendientes.add(etapa.etapa());
            }
            obligatoriasCompletas &= firmada;
        }
        return pendientes;
    }

    /** Primera etapa obligatoria anterior a `etapa` que falta firmar, si hay. */
    public static Optional<String> faltaAntes(List<Etapa> etapas, Map<String, LocalDateTime> firmas, String etapa) {
        for (Etapa e : etapas) {
            if (e.etapa().equals(etapa)) {
                return Optional.empty();
            }
            if (!e.opcional() && !firmas.containsKey(e.etapa())) {
                return Optional.of(e.etapa());
            }
        }
        return Optional.empty();
    }

    /** "13DPR..., 13DPR..." */
    public static String plazasTexto(List<MovimientoInterno> movimientos) {
        return movimientos.stream().map(m -> m.movimiento().cvePlaza()).distinct().collect(Collectors.joining(", "));
    }

    /** "01/09, 02/20" */
    public static String movimientosTexto(List<MovimientoInterno> movimientos) {
        return movimientos.stream()
                .map(m -> m.movimiento().movimiento() + "/" + m.movimiento().motivo())
                .collect(Collectors.joining(", "));
    }

    /** Cambia estatus y CT de la plaza, también en los JSON que leen las Consultas. */
    public void actualizarPlaza(int plazaId, int statusId, String statusName, String centerCode) {
        jdbc.sql("""
                        UPDATE demo_positions SET
                            status_id = :statusId,
                            status_name = :statusName,
                            center_code = :center,
                            summary = summary || jsonb_build_object('estatus', CAST(:statusName AS text),
                                                                    'centroTrabajo', CAST(:center AS text)),
                            details = details || jsonb_build_object('estatusId', CAST(:statusId AS integer),
                                                                    'estatus', CAST(:statusName AS text),
                                                                    'centroTrabajo', CAST(:center AS text))
                        WHERE id = :id
                        """)
                .param("statusId", statusId).param("statusName", statusName).param("center", centerCode)
                .param("id", plazaId).update();
    }

    private static final String FOLIO_SELECT = """
            SELECT f.acuerdo, f.status, f.rechazado, f.ts_captura, f.cat_nivel_id, n.nivel, f.empleado_id,
                   e.rfc, e.full_name, f.crea_empleado
            FROM fup_folios f
            LEFT JOIN cat_nivel_rh n ON n.id = f.cat_nivel_id
            LEFT JOIN demo_employees e ON e.id = f.empleado_id""";

    private static Folio mapFolio(ResultSet row, int n) throws SQLException {
        return new Folio(row.getInt("acuerdo"), row.getString("status"), row.getBoolean("rechazado"),
                row.getTimestamp("ts_captura").toLocalDateTime(), (Integer) row.getObject("cat_nivel_id"),
                row.getString("nivel"), (Integer) row.getObject("empleado_id"), row.getString("rfc"),
                row.getString("full_name"), row.getBoolean("crea_empleado"));
    }
}
