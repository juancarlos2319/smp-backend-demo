package mx.gob.seph.smp.demo.seguridad;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Qué módulos, banderas y etapas tiene un usuario (equivalente demo de `@RequiereModulo` del back real).
 * Un módulo asignado da acceso a sus ancestros; las banderas solo valen en el módulo asignado.
 */
@Service
public class PermisosService {

    /** Módulo visible para el usuario, con sus banderas combinadas de todos sus roles. */
    public record ModuloUsuario(
            int id, String codigo, String name, String description, String path, Integer parentId,
            String parentName, String icon, String materialIcon, boolean vista, Integer orden,
            Set<Permiso> permisos) {

        public Map<String, Boolean> permisosJson() {
            Map<String, Boolean> json = new LinkedHashMap<>();
            for (Permiso permiso : permisos) {
                json.put(permiso.key(), true);
            }
            return json;
        }
    }

    private final JdbcClient jdbc;

    public PermisosService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ModuloUsuario> modulos(long userId) {
        return jdbc.sql("""
                        WITH RECURSIVE asignados AS (
                            SELECT rm.module_id, rm.can_agregar, rm.can_editar, rm.can_eliminar, rm.can_autorizar,
                                   rm.can_ver_detalle_tecnico
                            FROM core_user_roles ur
                            JOIN core_roles r ON r.id = ur.role_id AND NOT r.deleted
                            JOIN core_role_modules rm ON rm.role_id = ur.role_id
                            WHERE ur.user_id = :userId
                            UNION ALL
                            SELECT ue.module_id, FALSE, FALSE, FALSE, FALSE, FALSE
                            FROM core_user_extras ue
                            WHERE ue.user_id = :userId
                        ), banderas AS (
                            SELECT module_id, bool_or(can_agregar) AS agregar, bool_or(can_editar) AS editar,
                                   bool_or(can_eliminar) AS eliminar, bool_or(can_autorizar) AS autorizar,
                                   bool_or(can_ver_detalle_tecnico) AS detalle
                            FROM asignados
                            GROUP BY module_id
                        ), arbol AS (
                            SELECT m.id, m.parent_id
                            FROM core_modules m
                            JOIN banderas b ON b.module_id = m.id
                            WHERE NOT m.deleted
                            UNION
                            SELECT p.id, p.parent_id
                            FROM core_modules p
                            JOIN arbol a ON a.parent_id = p.id
                            WHERE NOT p.deleted
                        )
                        SELECT m.id, m.codigo, m.name, m.description, m.path, m.parent_id, padre.name AS parent_name,
                               i.icon, i.material_icon, m.vista, m.orden,
                               COALESCE(b.agregar, FALSE) AS agregar, COALESCE(b.editar, FALSE) AS editar,
                               COALESCE(b.eliminar, FALSE) AS eliminar, COALESCE(b.autorizar, FALSE) AS autorizar,
                               COALESCE(b.detalle, FALSE) AS detalle
                        FROM (SELECT DISTINCT id FROM arbol) a
                        JOIN core_modules m ON m.id = a.id
                        LEFT JOIN core_modules padre ON padre.id = m.parent_id
                        LEFT JOIN core_icons i ON i.id = m.icon_id
                        LEFT JOIN banderas b ON b.module_id = m.id
                        ORDER BY m.orden NULLS LAST, m.id
                        """)
                .param("userId", userId)
                .query((row, n) -> {
                    Set<Permiso> permisos = EnumSet.noneOf(Permiso.class);
                    if (row.getBoolean("agregar")) permisos.add(Permiso.AGREGAR);
                    if (row.getBoolean("editar")) permisos.add(Permiso.EDITAR);
                    if (row.getBoolean("eliminar")) permisos.add(Permiso.ELIMINAR);
                    if (row.getBoolean("autorizar")) permisos.add(Permiso.AUTORIZAR);
                    if (row.getBoolean("detalle")) permisos.add(Permiso.VER_DETALLE_TECNICO);
                    return new ModuloUsuario(
                            row.getInt("id"), row.getString("codigo"), row.getString("name"),
                            row.getString("description"), row.getString("path"),
                            (Integer) row.getObject("parent_id"), row.getString("parent_name"),
                            row.getString("icon"), row.getString("material_icon"), row.getBoolean("vista"),
                            row.getObject("orden") == null ? null : row.getInt("orden"), permisos);
                })
                .list();
    }

    public boolean tieneModulo(long userId, String codigo) {
        return modulos(userId).stream().anyMatch(m -> m.codigo().equals(codigo));
    }

    public boolean tienePermiso(long userId, String codigo, Permiso permiso) {
        return modulos(userId).stream().anyMatch(m -> m.codigo().equals(codigo) && m.permisos().contains(permiso));
    }

    /** 403 si el usuario no tiene el módulo. */
    public void requiere(long userId, String codigo) {
        if (!tieneModulo(userId, codigo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso al módulo " + codigo + ".");
        }
    }

    /** 403 si el usuario no tiene alguno de los módulos. */
    public void requiereAlguno(long userId, String... codigos) {
        List<ModuloUsuario> modulos = modulos(userId);
        for (String codigo : codigos) {
            if (modulos.stream().anyMatch(m -> m.codigo().equals(codigo))) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este módulo.");
    }

    /** 403 si el usuario no tiene la bandera en el módulo. */
    public void requiere(long userId, String codigo, Permiso permiso) {
        if (!tienePermiso(userId, codigo, permiso)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Necesitas el permiso " + permiso.etiqueta() + " en el módulo " + codigo + ".");
        }
    }

    /** Etapas del flujo FUP de los roles del usuario, en el orden del flujo. */
    public List<String> etapas(long userId) {
        return jdbc.sql("""
                        SELECT DISTINCT e.etapa, e.orden
                        FROM core_user_roles ur
                        JOIN core_roles r ON r.id = ur.role_id AND NOT r.deleted
                        JOIN fup_etapa_roles er ON er.role_id = ur.role_id
                        JOIN fup_etapas e ON e.etapa = er.etapa
                        WHERE ur.user_id = :userId
                        ORDER BY e.orden
                        """)
                .param("userId", userId)
                .query((row, n) -> row.getString("etapa"))
                .list();
    }

    /**
     * Si alguno de los módulos donde el usuario tiene la bandera cubre el movimiento/motivo: por
     * `todos_movimientos` o por `core_module_movimientos` (sin motivo = todos los motivos).
     */
    public boolean cubre(long userId, Permiso permiso, String cveMovimiento, String cveMotivo) {
        return Boolean.TRUE.equals(jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM core_user_roles ur
                            JOIN core_roles r ON r.id = ur.role_id AND NOT r.deleted
                            JOIN core_role_modules rm ON rm.role_id = ur.role_id AND rm.%s
                            JOIN core_modules m ON m.id = rm.module_id AND NOT m.deleted
                            WHERE ur.user_id = :userId
                              AND (m.todos_movimientos OR EXISTS (
                                  SELECT 1 FROM core_module_movimientos mm
                                  WHERE mm.module_id = m.id AND mm.cve_movimiento = :mov
                                    AND (mm.cve_motivo IS NULL OR mm.cve_motivo = :motivo)))
                        )
                        """.formatted(permiso.column()))
                .param("userId", userId)
                .param("mov", cveMovimiento)
                .param("motivo", cveMotivo)
                .query(Boolean.class)
                .single());
    }
}
