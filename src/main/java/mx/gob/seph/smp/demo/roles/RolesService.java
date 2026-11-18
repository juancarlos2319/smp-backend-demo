package mx.gob.seph.smp.demo.roles;

import java.util.List;
import java.util.Map;

import mx.gob.seph.smp.demo.api.ReglaNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Roles (GESTION_ROLES), sus módulos con banderas y sus etapas del flujo FUP. */
@Service
public class RolesService {

    public record RoleRow(int id, String name, String description, String modulesName) {
    }

    /** RolModuloDTO: en la respuesta el módulo va en `id`; en el body, en `moduleId`. */
    public record RolModulo(
            int id, boolean agregar, boolean editar, boolean eliminar, boolean autorizar, boolean verDetalleTecnico) {
    }

    public record RoleDetail(int id, String name, String description, List<RolModulo> modules) {
    }

    public record RolModuloPayload(
            Integer moduleId, boolean agregar, boolean editar, boolean eliminar, boolean autorizar,
            boolean verDetalleTecnico) {
    }

    public record RolePayload(String name, String description, List<RolModuloPayload> modulos) {
    }

    public record RolEtapa(String etapa, boolean opcional, boolean asignada) {
    }

    private final JdbcClient jdbc;

    public RolesService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<RoleRow> list() {
        return jdbc.sql("""
                        SELECT r.id, r.name, r.description,
                               COALESCE(string_agg(m.name, ', ' ORDER BY m.name), '') AS modules_name
                        FROM core_roles r
                        LEFT JOIN core_role_modules rm ON rm.role_id = r.id
                        LEFT JOIN core_modules m ON m.id = rm.module_id AND NOT m.deleted
                        WHERE NOT r.deleted
                        GROUP BY r.id
                        ORDER BY r.name
                        """)
                .query((row, n) -> new RoleRow(row.getInt("id"), row.getString("name"),
                        row.getString("description"), row.getString("modules_name")))
                .list();
    }

    public RoleDetail get(int roleId) {
        List<RolModulo> modules = jdbc.sql("""
                        SELECT rm.module_id, rm.can_agregar, rm.can_editar, rm.can_eliminar, rm.can_autorizar,
                               rm.can_ver_detalle_tecnico
                        FROM core_role_modules rm
                        JOIN core_modules m ON m.id = rm.module_id AND NOT m.deleted
                        WHERE rm.role_id = :id
                        ORDER BY rm.module_id
                        """)
                .param("id", roleId)
                .query((row, n) -> new RolModulo(row.getInt("module_id"), row.getBoolean("can_agregar"),
                        row.getBoolean("can_editar"), row.getBoolean("can_eliminar"),
                        row.getBoolean("can_autorizar"), row.getBoolean("can_ver_detalle_tecnico")))
                .list();
        return jdbc.sql("SELECT id, name, description FROM core_roles WHERE id = :id AND NOT deleted")
                .param("id", roleId)
                .query((row, n) -> new RoleDetail(row.getInt("id"), row.getString("name"),
                        row.getString("description"), modules))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el rol."));
    }

    @Transactional
    public Map<String, Object> create(RolePayload payload) {
        String name = validar(payload, null);
        int id = jdbc.sql("INSERT INTO core_roles (name, description) VALUES (:name, :description) RETURNING id")
                .param("name", name)
                .param("description", blankToNull(payload.description()))
                .query(Integer.class).single();
        reemplazarModulos(id, payload.modulos());
        return Map.of("id", id);
    }

    @Transactional
    public void update(int roleId, RolePayload payload) {
        get(roleId);
        String name = validar(payload, roleId);
        jdbc.sql("UPDATE core_roles SET name = :name, description = :description WHERE id = :id")
                .param("name", name)
                .param("description", blankToNull(payload.description()))
                .param("id", roleId)
                .update();
        reemplazarModulos(roleId, payload.modulos());
    }

    /** Borrado lógico: los usuarios con el rol dejan de recibir sus módulos. */
    @Transactional
    public void remove(int roleId) {
        get(roleId);
        jdbc.sql("UPDATE core_roles SET deleted = TRUE WHERE id = :id").param("id", roleId).update();
    }

    public List<RolEtapa> etapas(int roleId) {
        get(roleId);
        return jdbc.sql("""
                        SELECT e.etapa, e.opcional, (er.role_id IS NOT NULL) AS asignada
                        FROM fup_etapas e
                        LEFT JOIN fup_etapa_roles er ON er.etapa = e.etapa AND er.role_id = :id
                        ORDER BY e.orden
                        """)
                .param("id", roleId)
                .query((row, n) -> new RolEtapa(row.getString("etapa"), row.getBoolean("opcional"),
                        row.getBoolean("asignada")))
                .list();
    }

    /** Reemplaza las etapas del rol (`[]` le quita todas). */
    @Transactional
    public List<RolEtapa> reemplazarEtapas(int roleId, List<String> etapas) {
        get(roleId);
        List<String> validas = jdbc.sql("SELECT etapa FROM fup_etapas").query(String.class).list();
        for (String etapa : etapas) {
            if (!validas.contains(etapa)) {
                throw new ReglaNegocioException("No existe la etapa '" + etapa + "'.");
            }
        }
        jdbc.sql("DELETE FROM fup_etapa_roles WHERE role_id = :id").param("id", roleId).update();
        for (String etapa : etapas.stream().distinct().toList()) {
            jdbc.sql("INSERT INTO fup_etapa_roles (role_id, etapa) VALUES (:id, :etapa)")
                    .param("id", roleId).param("etapa", etapa).update();
        }
        return etapas(roleId);
    }

    private String validar(RolePayload payload, Integer roleId) {
        String name = payload.name() == null ? "" : payload.name().trim();
        if (name.isEmpty()) {
            throw new ReglaNegocioException("El nombre del rol es obligatorio.");
        }
        int repetidos = jdbc.sql("""
                        SELECT count(*) FROM core_roles
                        WHERE lower(name) = lower(:name) AND NOT deleted AND id <> COALESCE(:id, -1)
                        """)
                .param("name", name).param("id", roleId).query(Integer.class).single();
        if (repetidos > 0) {
            throw new ReglaNegocioException("Ya existe un rol con el nombre " + name + ".");
        }
        for (RolModuloPayload modulo : payload.modulos() == null ? List.<RolModuloPayload>of() : payload.modulos()) {
            if (modulo.moduleId() == null || jdbc.sql("SELECT count(*) FROM core_modules WHERE id = :id AND NOT deleted")
                    .param("id", modulo.moduleId()).query(Integer.class).single() == 0) {
                throw new ReglaNegocioException("No existe el módulo " + modulo.moduleId() + ".");
            }
        }
        return name;
    }

    private void reemplazarModulos(int roleId, List<RolModuloPayload> modulos) {
        jdbc.sql("DELETE FROM core_role_modules WHERE role_id = :id").param("id", roleId).update();
        if (modulos == null) {
            return;
        }
        for (RolModuloPayload m : modulos) {
            jdbc.sql("""
                            INSERT INTO core_role_modules (role_id, module_id, can_agregar, can_editar, can_eliminar,
                                                           can_autorizar, can_ver_detalle_tecnico)
                            VALUES (:role, :module, :agregar, :editar, :eliminar, :autorizar, :detalle)
                            ON CONFLICT (role_id, module_id) DO NOTHING
                            """)
                    .param("role", roleId).param("module", m.moduleId())
                    .param("agregar", m.agregar()).param("editar", m.editar()).param("eliminar", m.eliminar())
                    .param("autorizar", m.autorizar()).param("detalle", m.verDetalleTecnico())
                    .update();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
