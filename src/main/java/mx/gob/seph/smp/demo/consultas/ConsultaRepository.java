package mx.gob.seph.smp.demo.consultas;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.Categoria;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.CategoriaConcepto;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.CentroTrabajoDetalle;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.CentroTrabajoPlaza;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.Concepto;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.EmpleadoDatos;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.EmpleadoOpcion;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.EmpleadoPlaza;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.EmpleadoResumen;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.HistorialMovimiento;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.PlazaCompatibilidad;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.PlazaDetalle;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.PlazaOcupante;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.PlazaResumen;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ConsultaRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public ConsultaRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<EmpleadoOpcion> employeeOptions() {
        return jdbc.sql("""
                        SELECT id, full_name, rfc, active
                        FROM demo_employees
                        ORDER BY full_name
                        """)
                .query((row, rowNumber) ->
                        new EmpleadoOpcion(row.getInt("id"), row.getString("full_name"),
                                row.getString("rfc"), row.getBoolean("active")))
                .list();
    }

    public List<EmpleadoResumen> employeeSummaries() {
        return jdbc.sql("""
                        SELECT id, rfc, curp, full_name, active
                        FROM demo_employees
                        ORDER BY id
                        """)
                .query((row, rowNumber) ->
                        new EmpleadoResumen(row.getInt("id"), row.getString("rfc"), row.getString("curp"),
                                row.getString("full_name"), row.getBoolean("active")))
                .list();
    }

    public Optional<EmpleadoDatos> employeeDetails(int id) {
        return jsonOne("SELECT details::text AS payload FROM demo_employees WHERE id = :id",
                "id", id, EmpleadoDatos.class);
    }

    public List<EmpleadoPlaza> employeePositions(int employeeId) {
        return jsonList("""
                SELECT employee_position::text AS payload
                FROM demo_position_occupants
                WHERE employee_id = :employeeId
                ORDER BY position_id
                """, "employeeId", employeeId, EmpleadoPlaza.class);
    }

    /** Historial del legacy más los movimientos de folios FUP (procesados o en trámite; sin los dados de baja). */
    public List<HistorialMovimiento> employeeHistory(int employeeId) {
        return jsonList("""
                SELECT payload FROM (
                    SELECT id AS orden, data::text AS payload
                    FROM demo_movement_history
                    WHERE employee_id = :employeeId
                    UNION ALL
                    """ + FUP_HISTORY + """
                    WHERE ff.empleado_id = :employeeId AND NOT ff.deleted
                ) t
                ORDER BY orden
                """, "employeeId", employeeId, HistorialMovimiento.class);
    }

    private static final String FUP_HISTORY = """
            SELECT 1000000 + fm.id AS orden,
                   jsonb_build_object(
                       'id', 1000000 + fm.id, 'acuerdo', ff.acuerdo, 'plazaId', fm.tab_plaza_id, 'cvePlaza', p.code,
                       'categoria', p.category_code, 'empleadoId', ff.empleado_id, 'rfc', e.rfc,
                       'nombreCompleto', e.full_name, 'movimiento', COALESCE(cm.descripcion, fm.cve_movimiento),
                       'motivo', COALESCE(mo.descripcion, fm.cve_motivo), 'qnaIni', fm.qna_ini, 'qnaFin', fm.qna_fin,
                       'centroTrabajo', fm.cct_clave, 'status', ff.status, 'rechazado', ff.rechazado
                   )::text AS payload
            FROM fup_movimientos fm
            JOIN fup_folios ff ON ff.acuerdo = fm.acuerdo
            JOIN demo_positions p ON p.id = fm.tab_plaza_id
            LEFT JOIN demo_employees e ON e.id = ff.empleado_id
            LEFT JOIN cat_movimientos cm ON cm.cve = fm.cve_movimiento
            LEFT JOIN cat_motivos mo ON mo.cve_movimiento = fm.cve_movimiento AND mo.cve_motivo = fm.cve_motivo
            """;

    public List<PlazaCompatibilidad> employeeCompatibility(int employeeId) {
        return jdbc.sql("""
                SELECT jsonb_build_object(
                    'plazaId', p.id,
                    'cvePlaza', p.code,
                    'codPago', p.details->>'codPago',
                    'unidad', p.unit_code,
                    'subunidad', p.subunit_code,
                    'categoria', p.category_code,
                    'categoriaDescripcion', c.details->>'descripcion',
                    'horas', p.hours,
                    'horasJornada', (p.compatibility->>'horasJornada')::integer,
                    'hsemax', (p.compatibility->>'hsemax')::integer,
                    'hclmax', (p.compatibility->>'hclmax')::integer,
                    'centroTrabajo', p.center_code,
                    'estatusId', p.status_id,
                    'estatus', p.status_name
                )::text AS payload
                FROM demo_positions p
                JOIN demo_position_occupants o ON o.position_id = p.id
                JOIN demo_categories c ON c.code = p.category_code
                WHERE o.employee_id = :employeeId
                ORDER BY p.id
                """)
                .param("employeeId", employeeId)
                .query((row, rowNumber) -> readJson(row, PlazaCompatibilidad.class))
                .list();
    }

    public List<PlazaResumen> positionSummaries() {
        return jsonList("SELECT summary::text AS payload FROM demo_positions ORDER BY id", PlazaResumen.class);
    }

    public Optional<PlazaDetalle> positionDetails(int id) {
        return jsonOne("SELECT details::text AS payload FROM demo_positions WHERE id = :id",
                "id", id, PlazaDetalle.class);
    }

    public List<PlazaOcupante> positionOccupants(int id) {
        return jsonList("""
                SELECT occupant::text AS payload
                FROM demo_position_occupants
                WHERE position_id = :id
                ORDER BY employee_id
                """, "id", id, PlazaOcupante.class);
    }

    public List<HistorialMovimiento> positionHistory(int id, String centerCode) {
        String sql = """
                        SELECT payload FROM (
                            SELECT id AS orden, data::text AS payload
                            FROM demo_movement_history
                            WHERE position_id = :id
                        """ + (centerCode == null ? "" : " AND upper(center_code) = upper(:centerCode)\n") + """
                            UNION ALL
                        """ + FUP_HISTORY + """
                            WHERE fm.tab_plaza_id = :id AND NOT ff.deleted
                        """ + (centerCode == null ? "" : " AND upper(fm.cct_clave) = upper(:centerCode)\n") + """
                        ) t
                        ORDER BY orden
                        """;
        var statement = jdbc.sql(sql).param("id", id);
        if (centerCode != null) {
            statement.param("centerCode", centerCode);
        }
        return statement.query((row, rowNumber) -> readJson(row, HistorialMovimiento.class)).list();
    }

    public List<CentroTrabajoDetalle> workCenters() {
        return jsonList("SELECT details::text AS payload FROM demo_work_centers ORDER BY code",
                CentroTrabajoDetalle.class);
    }

    public Optional<CentroTrabajoDetalle> workCenter(String code) {
        return jsonOne("""
                SELECT details::text AS payload
                FROM demo_work_centers
                WHERE upper(code) = upper(:code)
                """, "code", code, CentroTrabajoDetalle.class);
    }

    public List<CentroTrabajoPlaza> workCenterPositions(String code, Integer status) {
        String sql = """
                        SELECT jsonb_build_object(
                            'id', p.id,
                            'cvePlaza', p.code,
                            'categoria', p.category_code,
                            'categoriaDescripcion', c.details->>'descripcion',
                            'horas', p.hours,
                            'estatusId', p.status_id,
                            'estatus', p.status_name,
                            'ocupantes', COALESCE(string_agg(e.rfc, ', ' ORDER BY e.rfc), '')
                        )::text AS payload
                        FROM demo_positions p
                        JOIN demo_categories c ON c.code = p.category_code
                        LEFT JOIN demo_position_occupants o ON o.position_id = p.id
                        LEFT JOIN demo_employees e ON e.id = o.employee_id
                        WHERE upper(p.center_code) = upper(:code)
                        """ + (status == null ? "" : " AND p.status_id = :status\n") + """
                        GROUP BY p.id, c.code
                        ORDER BY p.id
                        """;
        var statement = jdbc.sql(sql).param("code", code);
        if (status != null) {
            statement.param("status", status);
        }
        return statement.query((row, rowNumber) -> readJson(row, CentroTrabajoPlaza.class)).list();
    }

    public List<Categoria> categories() {
        return jsonList("SELECT details::text AS payload FROM demo_categories ORDER BY code", Categoria.class);
    }

    public Optional<Categoria> category(String code) {
        return jsonOne("""
                SELECT details::text AS payload
                FROM demo_categories
                WHERE upper(code) = upper(:code)
                """, "code", code, Categoria.class);
    }

    public List<CategoriaConcepto> categoryConcepts(String code) {
        return jsonList("""
                SELECT data::text AS payload
                FROM demo_category_concepts
                WHERE upper(category_code) = upper(:code)
                ORDER BY id
                """, "code", code, CategoriaConcepto.class);
    }

    public List<Concepto> concepts() {
        return jsonList("SELECT details::text AS payload FROM demo_concepts ORDER BY perc_ded, code", Concepto.class);
    }

    private <T> List<T> jsonList(String sql, Class<T> type) {
        return jdbc.sql(sql).query((row, rowNumber) -> readJson(row, type)).list();
    }

    private <T> List<T> jsonList(String sql, String parameter, Object value, Class<T> type) {
        return jdbc.sql(sql).param(parameter, value)
                .query((row, rowNumber) -> readJson(row, type)).list();
    }

    private <T> Optional<T> jsonOne(String sql, String parameter, Object value, Class<T> type) {
        return jdbc.sql(sql).param(parameter, value)
                .query((row, rowNumber) -> readJson(row, type)).optional();
    }

    private <T> T readJson(ResultSet row, Class<T> type) throws java.sql.SQLException {
        try {
            return objectMapper.readValue(row.getString("payload"), type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("No se pudo leer un registro de datos demo.", exception);
        }
    }
}
