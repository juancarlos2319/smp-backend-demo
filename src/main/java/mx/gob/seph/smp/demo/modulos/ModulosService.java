package mx.gob.seph.smp.demo.modulos;

import java.util.List;
import java.util.Map;
import java.util.Set;

import mx.gob.seph.smp.demo.api.ReglaNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Catálogo de módulos (GESTION_MODULOS): menú, roles que los tienen y movimientos FUP que cubren. */
@Service
public class ModulosService {
    /** Módulos de los que depende el back: no se les cambia el código ni se eliminan. */
    public static final Set<String> SYSTEM_MODULES = Set.of(
            "GESTION", "GESTION_USUARIOS", "GESTION_ROLES", "GESTION_MODULOS", "FUP_MOVIMIENTOS", "EMPLEADOS",
            "SALIR");

    public record ModuleOption(
            int id, String name, String codigo, String path, String parent, Integer parentId, boolean vista,
            String description, Integer iconId, String icon, String materialIcon, Integer orden) {
    }

    public record CoreIcon(int id, String name, String icon, String materialIcon) {
    }

    public record RoleRef(int id, String name) {
    }

    public record Movimiento(String cveMovimiento, String cveMotivo) {
    }

    public record ModuleDetail(
            int id, String name, String codigo, String path, String description, boolean vista, Integer parentId,
            Integer iconId, String icon, String materialIcon, Integer orden, List<RoleRef> roles,
            List<Movimiento> movimientos, boolean todosLosMovimientos) {
    }

    public record ModulePayload(
            String name, String codigo, String path, String description, Boolean visible, Integer iconId,
            Integer parentId, Integer orden, List<Integer> rolesId, List<Movimiento> movimientos,
            Boolean todosLosMovimientos) {
    }

    private final JdbcClient jdbc;

    public ModulosService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ModuleOption> list() {
        return jdbc.sql("""
                        SELECT m.id, m.name, m.codigo, m.path, p.name AS parent, m.parent_id, m.vista, m.description,
                               m.icon_id, i.icon, i.material_icon, m.orden
                        FROM core_modules m
                        LEFT JOIN core_modules p ON p.id = m.parent_id
                        LEFT JOIN core_icons i ON i.id = m.icon_id
                        WHERE NOT m.deleted
                        ORDER BY m.orden NULLS LAST, m.name
                        """)
                .query((row, n) -> new ModuleOption(row.getInt("id"), row.getString("name"),
                        row.getString("codigo"), row.getString("path"), row.getString("parent"),
                        (Integer) row.getObject("parent_id"), row.getBoolean("vista"),
                        row.getString("description"), (Integer) row.getObject("icon_id"), row.getString("icon"),
                        row.getString("material_icon"), orden(row)))
                .list();
    }

    public List<CoreIcon> icons() {
        return jdbc.sql("SELECT id, name, icon, material_icon FROM core_icons ORDER BY name")
                .query((row, n) -> new CoreIcon(row.getInt("id"), row.getString("name"), row.getString("icon"),
                        row.getString("material_icon")))
                .list();
    }

    public ModuleDetail get(int moduleId) {
        List<RoleRef> roles = jdbc.sql("""
                        SELECT r.id, r.name FROM core_role_modules rm
                        JOIN core_roles r ON r.id = rm.role_id AND NOT r.deleted
                        WHERE rm.module_id = :id ORDER BY r.name
                        """)
                .param("id", moduleId)
                .query((row, n) -> new RoleRef(row.getInt("id"), row.getString("name"))).list();
        List<Movimiento> movimientos = jdbc.sql("""
                        SELECT cve_movimiento, cve_motivo FROM core_module_movimientos
                        WHERE module_id = :id ORDER BY cve_movimiento, cve_motivo NULLS FIRST
                        """)
                .param("id", moduleId)
                .query((row, n) -> new Movimiento(row.getString("cve_movimiento"), row.getString("cve_motivo")))
                .list();
        return jdbc.sql("""
                        SELECT m.id, m.name, m.codigo, m.path, m.description, m.vista, m.parent_id, m.icon_id,
                               i.icon, i.material_icon, m.orden, m.todos_movimientos
                        FROM core_modules m
                        LEFT JOIN core_icons i ON i.id = m.icon_id
                        WHERE m.id = :id AND NOT m.deleted
                        """)
                .param("id", moduleId)
                .query((row, n) -> new ModuleDetail(row.getInt("id"), row.getString("name"),
                        row.getString("codigo"), row.getString("path"), row.getString("description"),
                        row.getBoolean("vista"), (Integer) row.getObject("parent_id"),
                        (Integer) row.getObject("icon_id"), row.getString("icon"), row.getString("material_icon"),
                        orden(row), roles, movimientos, row.getBoolean("todos_movimientos")))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el módulo."));
    }

    @Transactional
    public Map<String, Object> create(ModulePayload payload) {
        String codigo = validar(payload, null);
        int id = jdbc.sql("""
                        INSERT INTO core_modules (codigo, name, path, description, vista, parent_id, icon_id, orden,
                                                  todos_movimientos)
                        VALUES (:codigo, :name, :path, :description, :vista, :parent, :icon, :orden, :todos)
                        RETURNING id
                        """)
                .param("codigo", codigo)
                .param("name", payload.name().trim())
                .param("path", blankToNull(payload.path()))
                .param("description", blankToNull(payload.description()))
                .param("vista", payload.visible() == null || payload.visible())
                .param("parent", payload.parentId())
                .param("icon", payload.iconId())
                .param("orden", payload.orden())
                .param("todos", Boolean.TRUE.equals(payload.todosLosMovimientos()))
                .query(Integer.class).single();
        reemplazarRolesYMovimientos(id, payload);
        return Map.of("id", id);
    }

    @Transactional
    public void update(int moduleId, ModulePayload payload) {
        ModuleDetail actual = get(moduleId);
        String codigo = validar(payload, moduleId);
        if (SYSTEM_MODULES.contains(actual.codigo()) && !actual.codigo().equals(codigo)) {
            throw new ReglaNegocioException("No se puede cambiar el código del módulo de sistema " + actual.codigo() + ".");
        }
        jdbc.sql("""
                        UPDATE core_modules SET codigo = :codigo, name = :name, path = :path, description = :description,
                               vista = :vista, parent_id = :parent, icon_id = :icon, orden = :orden,
                               todos_movimientos = :todos
                        WHERE id = :id
                        """)
                .param("codigo", codigo)
                .param("name", payload.name().trim())
                .param("path", blankToNull(payload.path()))
                .param("description", blankToNull(payload.description()))
                .param("vista", payload.visible() == null || payload.visible())
                .param("parent", payload.parentId())
                .param("icon", payload.iconId())
                .param("orden", payload.orden())
                .param("todos", Boolean.TRUE.equals(payload.todosLosMovimientos()))
                .param("id", moduleId)
                .update();
        reemplazarRolesYMovimientos(moduleId, payload);
    }

    /** Borrado lógico de toda la rama. */
    @Transactional
    public int remove(int moduleId) {
        ModuleDetail actual = get(moduleId);
        if (SYSTEM_MODULES.contains(actual.codigo())) {
            throw new ReglaNegocioException("El módulo " + actual.codigo() + " es de sistema y no se puede eliminar.");
        }
        List<String> sistema = jdbc.sql("""
                        WITH RECURSIVE rama AS (
                            SELECT id, codigo FROM core_modules WHERE id = :id
                            UNION
                            SELECT m.id, m.codigo FROM core_modules m JOIN rama r ON m.parent_id = r.id
                            WHERE NOT m.deleted
                        )
                        SELECT codigo FROM rama
                        """)
                .param("id", moduleId).query(String.class).list().stream()
                .filter(SYSTEM_MODULES::contains).toList();
        if (!sistema.isEmpty()) {
            throw new ReglaNegocioException("La rama contiene módulos de sistema: " + String.join(", ", sistema) + ".");
        }
        return jdbc.sql("""
                        WITH RECURSIVE rama AS (
                            SELECT id FROM core_modules WHERE id = :id
                            UNION
                            SELECT m.id FROM core_modules m JOIN rama r ON m.parent_id = r.id WHERE NOT m.deleted
                        )
                        UPDATE core_modules SET deleted = TRUE WHERE id IN (SELECT id FROM rama)
                        """)
                .param("id", moduleId).update();
    }

    private String validar(ModulePayload payload, Integer moduleId) {
        if (payload.name() == null || payload.name().isBlank()) {
            throw new ReglaNegocioException("El nombre del módulo es obligatorio.");
        }
        String codigo = payload.codigo() == null ? "" : payload.codigo().trim().toUpperCase();
        if (!codigo.matches("[A-Z0-9_]{2,60}")) {
            throw new ReglaNegocioException("El código debe tener de 2 a 60 letras, números o guiones bajos.");
        }
        int repetidos = jdbc.sql("""
                        SELECT count(*) FROM core_modules
                        WHERE codigo = :codigo AND NOT deleted AND id <> COALESCE(:id, -1)
                        """)
                .param("codigo", codigo).param("id", moduleId).query(Integer.class).single();
        if (repetidos > 0) {
            throw new ReglaNegocioException("Ya existe un módulo con el código " + codigo + ".");
        }
        if (payload.orden() != null && (payload.orden() < 0 || payload.orden() > 32767)) {
            throw new ReglaNegocioException("El orden debe ir de 0 a 32767.");
        }
        if (payload.parentId() != null) {
            if (payload.parentId().equals(moduleId)) {
                throw new ReglaNegocioException("Un módulo no puede ser su propio padre.");
            }
            if (existe("SELECT count(*) FROM core_modules WHERE id = :id AND NOT deleted", payload.parentId())) {
                if (moduleId != null && esDescendiente(payload.parentId(), moduleId)) {
                    throw new ReglaNegocioException("El padre no puede ser un módulo de su propia rama.");
                }
            } else {
                throw new ReglaNegocioException("No existe el módulo padre " + payload.parentId() + ".");
            }
        }
        if (payload.iconId() != null && !existe("SELECT count(*) FROM core_icons WHERE id = :id", payload.iconId())) {
            throw new ReglaNegocioException("No existe el ícono " + payload.iconId() + ".");
        }
        for (Integer roleId : payload.rolesId() == null ? List.<Integer>of() : payload.rolesId()) {
            if (!existe("SELECT count(*) FROM core_roles WHERE id = :id AND NOT deleted", roleId)) {
                throw new ReglaNegocioException("No existe el rol " + roleId + ".");
            }
        }
        for (Movimiento m : payload.movimientos() == null ? List.<Movimiento>of() : payload.movimientos()) {
            if (m.cveMovimiento() == null || m.cveMovimiento().length() != 2
                    || (m.cveMotivo() != null && !m.cveMotivo().isEmpty() && m.cveMotivo().length() != 2)) {
                throw new ReglaNegocioException("Las claves de movimiento y motivo deben tener 2 caracteres.");
            }
        }
        return codigo;
    }

    /** `rolesId` y `movimientos` reemplazan lo actual; un rol nuevo recibe el módulo sin banderas. */
    private void reemplazarRolesYMovimientos(int moduleId, ModulePayload payload) {
        List<Integer> roles = payload.rolesId() == null ? List.of() : payload.rolesId().stream().distinct().toList();
        List<Integer> actuales = jdbc.sql("SELECT role_id FROM core_role_modules WHERE module_id = :id")
                .param("id", moduleId).query(Integer.class).list();
        for (Integer roleId : actuales) {
            if (!roles.contains(roleId)) {
                jdbc.sql("DELETE FROM core_role_modules WHERE module_id = :id AND role_id = :role")
                        .param("id", moduleId).param("role", roleId).update();
            }
        }
        for (Integer roleId : roles) {
            jdbc.sql("""
                            INSERT INTO core_role_modules (role_id, module_id) VALUES (:role, :id)
                            ON CONFLICT (role_id, module_id) DO NOTHING
                            """)
                    .param("role", roleId).param("id", moduleId).update();
        }
        jdbc.sql("DELETE FROM core_module_movimientos WHERE module_id = :id").param("id", moduleId).update();
        for (Movimiento m : payload.movimientos() == null ? List.<Movimiento>of() : payload.movimientos()) {
            jdbc.sql("INSERT INTO core_module_movimientos (module_id, cve_movimiento, cve_motivo) VALUES (:id, :mov, :mot)")
                    .param("id", moduleId).param("mov", m.cveMovimiento())
                    .param("mot", blankToNull(m.cveMotivo())).update();
        }
    }

    private boolean esDescendiente(int candidato, int moduleId) {
        return jdbc.sql("""
                        WITH RECURSIVE rama AS (
                            SELECT id FROM core_modules WHERE parent_id = :id
                            UNION
                            SELECT m.id FROM core_modules m JOIN rama r ON m.parent_id = r.id
                        )
                        SELECT count(*) FROM rama WHERE id = :candidato
                        """)
                .param("id", moduleId).param("candidato", candidato).query(Integer.class).single() > 0;
    }

    private boolean existe(String sql, Object id) {
        return jdbc.sql(sql).param("id", id).query(Integer.class).single() > 0;
    }

    private static Integer orden(java.sql.ResultSet row) throws java.sql.SQLException {
        return row.getObject("orden") == null ? null : row.getInt("orden");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
