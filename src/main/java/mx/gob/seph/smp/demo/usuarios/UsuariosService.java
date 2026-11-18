package mx.gob.seph.smp.demo.usuarios;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import mx.gob.seph.smp.demo.api.ReglaNegocioException;
import mx.gob.seph.smp.demo.auth.JwtSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Gestión de usuarios (GESTION_USUARIOS). */
@Service
public class UsuariosService {

    public record UserRow(
            long id, String username, String email, Integer area, String task, Integer catEmpleadoId,
            boolean active, String principal, String areaNombre, String rolesName) {
    }

    public record UserDetail(
            long id, String username, String email, Integer area, String task, Integer catEmpleadoId,
            boolean active, String principal, @JsonProperty("isVerified") boolean isVerified,
            List<Integer> roles, List<Integer> extras) {
    }

    /** UserDTO: en el PATCH un nulo no cambia el campo, salvo `principal` y `area`. */
    public record UserPayload(
            String user, String password, Integer catEmpleadoId, Integer area, String task,
            List<Integer> roles, List<Integer> extras, String principal, Boolean active,
            @JsonProperty("isVerified") Boolean isVerified) {
    }

    public record NivelRh(int id, String nivel) {
    }

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtSessionService jwtSessionService;

    public UsuariosService(JdbcClient jdbc, PasswordEncoder passwordEncoder, JwtSessionService jwtSessionService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtSessionService = jwtSessionService;
    }

    public List<UserRow> list() {
        return jdbc.sql("""
                        SELECT u.id, u.username, u.email, u.area, u.task, u.cat_empleado_id, u.active, u.principal,
                               n.nivel AS area_nombre,
                               COALESCE(string_agg(r.name, ', ' ORDER BY r.name), '') AS roles_name
                        FROM core_users u
                        LEFT JOIN cat_nivel_rh n ON n.id = u.area
                        LEFT JOIN core_user_roles ur ON ur.user_id = u.id
                        LEFT JOIN core_roles r ON r.id = ur.role_id AND NOT r.deleted
                        WHERE NOT u.deleted
                        GROUP BY u.id, n.nivel
                        ORDER BY u.username
                        """)
                .query((row, n) -> new UserRow(row.getLong("id"), row.getString("username"),
                        row.getString("email"), (Integer) row.getObject("area"), row.getString("task"),
                        (Integer) row.getObject("cat_empleado_id"), row.getBoolean("active"),
                        row.getString("principal"), row.getString("area_nombre"), row.getString("roles_name")))
                .list();
    }

    public List<NivelRh> niveles() {
        return jdbc.sql("SELECT id, nivel FROM cat_nivel_rh ORDER BY id")
                .query((row, n) -> new NivelRh(row.getInt("id"), row.getString("nivel"))).list();
    }

    public UserDetail get(long userId) {
        return jdbc.sql("""
                        SELECT id, username, email, area, task, cat_empleado_id, active, principal, is_verified
                        FROM core_users WHERE id = :id AND NOT deleted
                        """)
                .param("id", userId)
                .query((row, n) -> new UserDetail(row.getLong("id"), row.getString("username"),
                        row.getString("email"), (Integer) row.getObject("area"), row.getString("task"),
                        (Integer) row.getObject("cat_empleado_id"), row.getBoolean("active"),
                        row.getString("principal"), row.getBoolean("is_verified"),
                        ids("SELECT role_id FROM core_user_roles WHERE user_id = :id ORDER BY role_id", userId),
                        ids("SELECT module_id FROM core_user_extras WHERE user_id = :id ORDER BY module_id", userId)))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el usuario."));
    }

    @Transactional
    public Map<String, Object> create(UserPayload payload) {
        String username = payload.user() == null ? "" : payload.user().trim();
        if (username.isEmpty()) {
            throw new ReglaNegocioException("El usuario es obligatorio.");
        }
        if (payload.password() == null || payload.password().length() < 8) {
            throw new ReglaNegocioException("La contraseña debe tener al menos 8 caracteres.");
        }
        if (existeUsername(username, null)) {
            throw new ReglaNegocioException("Ya existe un usuario con el nombre " + username + ".");
        }
        validar(payload, true);
        long id = jdbc.sql("""
                        INSERT INTO core_users (username, email, password_hash, area, task, cat_empleado_id, active,
                                                principal, is_verified, is_password)
                        VALUES (:username, :email, :hash, :area, :task, :empleado, :active, :principal, :verified, TRUE)
                        RETURNING id
                        """)
                .param("username", username)
                .param("email", username.contains("@") ? username : null)
                .param("hash", passwordEncoder.encode(payload.password()))
                .param("area", payload.area())
                .param("task", blankToNull(payload.task()))
                .param("empleado", payload.catEmpleadoId())
                .param("active", payload.active() == null || payload.active())
                .param("principal", blankToNull(payload.principal()))
                .param("verified", Boolean.TRUE.equals(payload.isVerified()))
                .query(Long.class).single();
        reemplazarRolesYExtras(id, payload);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        return data;
    }

    @Transactional
    public void update(long userId, UserPayload payload) {
        get(userId);
        validar(payload, true);
        jdbc.sql("""
                        UPDATE core_users SET
                            cat_empleado_id = COALESCE(:empleado, cat_empleado_id),
                            area = :area,
                            task = COALESCE(:task, task),
                            principal = :principal,
                            active = COALESCE(:active, active),
                            is_verified = COALESCE(:verified, is_verified)
                        WHERE id = :id
                        """)
                .param("empleado", payload.catEmpleadoId())
                .param("area", payload.area())
                .param("task", blankToNull(payload.task()))
                .param("principal", blankToNull(payload.principal()))
                .param("active", payload.active())
                .param("verified", payload.isVerified())
                .param("id", userId)
                .update();
        reemplazarRolesYExtras(userId, payload);
        if (Boolean.FALSE.equals(payload.active())) {
            jwtSessionService.revokeAll(userId);
        }
    }

    @Transactional
    public void remove(long userId, long currentUserId) {
        if (userId == currentUserId) {
            throw new ReglaNegocioException("No puedes eliminar tu propio usuario.");
        }
        get(userId);
        jdbc.sql("UPDATE core_users SET deleted = TRUE, active = FALSE WHERE id = :id").param("id", userId).update();
        jdbc.sql("DELETE FROM core_user_roles WHERE user_id = :id").param("id", userId).update();
        jdbc.sql("DELETE FROM core_user_extras WHERE user_id = :id").param("id", userId).update();
        jwtSessionService.revokeAll(userId);
    }

    private void validar(UserPayload payload, boolean empleadoObligatorio) {
        if (payload.catEmpleadoId() == null) {
            if (empleadoObligatorio) {
                throw new ReglaNegocioException("El empleado es obligatorio.");
            }
        } else if (count("SELECT count(*) FROM demo_employees WHERE id = :id", payload.catEmpleadoId()) == 0) {
            throw new ReglaNegocioException("No existe el empleado " + payload.catEmpleadoId() + ".");
        }
        if (payload.area() != null && count("SELECT count(*) FROM cat_nivel_rh WHERE id = :id", payload.area()) == 0) {
            throw new ReglaNegocioException("No existe el nivel " + payload.area() + ".");
        }
        for (Integer roleId : nullToEmpty(payload.roles())) {
            if (count("SELECT count(*) FROM core_roles WHERE id = :id AND NOT deleted", roleId) == 0) {
                throw new ReglaNegocioException("No existe el rol " + roleId + ".");
            }
        }
        for (Integer moduleId : nullToEmpty(payload.extras())) {
            if (count("SELECT count(*) FROM core_modules WHERE id = :id AND NOT deleted", moduleId) == 0) {
                throw new ReglaNegocioException("No existe el módulo " + moduleId + ".");
            }
        }
    }

    private void reemplazarRolesYExtras(long userId, UserPayload payload) {
        if (payload.roles() != null) {
            jdbc.sql("DELETE FROM core_user_roles WHERE user_id = :id").param("id", userId).update();
            for (Integer roleId : payload.roles().stream().distinct().toList()) {
                jdbc.sql("INSERT INTO core_user_roles (user_id, role_id) VALUES (:u, :r)")
                        .param("u", userId).param("r", roleId).update();
            }
        }
        if (payload.extras() != null) {
            jdbc.sql("DELETE FROM core_user_extras WHERE user_id = :id").param("id", userId).update();
            for (Integer moduleId : payload.extras().stream().distinct().toList()) {
                jdbc.sql("INSERT INTO core_user_extras (user_id, module_id) VALUES (:u, :m)")
                        .param("u", userId).param("m", moduleId).update();
            }
        }
    }

    private boolean existeUsername(String username, Long exceptId) {
        return jdbc.sql("""
                        SELECT count(*) FROM core_users
                        WHERE lower(username) = lower(:username) AND NOT deleted AND id <> COALESCE(:except, -1)
                        """)
                .param("username", username).param("except", exceptId).query(Integer.class).single() > 0;
    }

    private List<Integer> ids(String sql, long userId) {
        return jdbc.sql(sql).param("id", userId).query(Integer.class).list();
    }

    private int count(String sql, Object id) {
        return jdbc.sql(sql).param("id", id).query(Integer.class).single();
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
