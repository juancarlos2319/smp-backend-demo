package mx.gob.seph.smp.demo.cuenta;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acceso a `core_users` para la sesión y la cuenta propia. */
@Repository
public class CuentaRepository {

    public record Cuenta(
            long id, String username, String email, String passwordHash, Integer area, boolean active,
            String principal, boolean verified, boolean passwordSet) {
    }

    private final JdbcClient jdbc;

    public CuentaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Cuenta> porUsername(String username) {
        return jdbc.sql(SELECT + " WHERE lower(username) = lower(:username) AND NOT deleted")
                .param("username", username)
                .query(this::map)
                .optional();
    }

    public Optional<Cuenta> porId(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id AND NOT deleted").param("id", id).query(this::map).optional();
    }

    public List<Integer> roles(long userId) {
        return jdbc.sql("""
                        SELECT ur.role_id FROM core_user_roles ur
                        JOIN core_roles r ON r.id = ur.role_id AND NOT r.deleted
                        WHERE ur.user_id = :id ORDER BY ur.role_id
                        """)
                .param("id", userId).query(Integer.class).list();
    }

    public List<Integer> extras(long userId) {
        return jdbc.sql("SELECT module_id FROM core_user_extras WHERE user_id = :id ORDER BY module_id")
                .param("id", userId).query(Integer.class).list();
    }

    public void cambiarPassword(long userId, String hash) {
        jdbc.sql("UPDATE core_users SET password_hash = :hash, is_password = TRUE WHERE id = :id")
                .param("hash", hash).param("id", userId).update();
    }

    public void marcarVerificado(long userId) {
        jdbc.sql("UPDATE core_users SET is_verified = TRUE WHERE id = :id").param("id", userId).update();
        jdbc.sql("DELETE FROM core_verification_codes WHERE user_id = :id").param("id", userId).update();
    }

    public void guardarCodigo(long userId, String code, LocalDateTime expiresAt) {
        jdbc.sql("""
                        INSERT INTO core_verification_codes (user_id, code, expires_at) VALUES (:id, :code, :exp)
                        ON CONFLICT (user_id) DO UPDATE SET code = EXCLUDED.code, expires_at = EXCLUDED.expires_at
                        """)
                .param("id", userId).param("code", code).param("exp", expiresAt).update();
    }

    public boolean codigoValido(long userId, String code) {
        return jdbc.sql("""
                        SELECT count(*) FROM core_verification_codes
                        WHERE user_id = :id AND code = :code AND expires_at > now()
                        """)
                .param("id", userId).param("code", code).query(Integer.class).single() > 0;
    }

    public void guardarRecuperacion(String folio, String ts, long userId, LocalDateTime expiresAt) {
        jdbc.sql("""
                        INSERT INTO core_password_recovery (folio, ts, user_id, expires_at)
                        VALUES (:folio, :ts, :id, :exp)
                        """)
                .param("folio", folio).param("ts", ts).param("id", userId).param("exp", expiresAt).update();
    }

    /** Usuario de la liga si sigue vigente y no se ha usado. */
    public Optional<Long> recuperacionVigente(String folio, String ts) {
        return jdbc.sql("""
                        SELECT user_id FROM core_password_recovery
                        WHERE folio = :folio AND ts = :ts AND NOT used AND expires_at > now()
                        """)
                .param("folio", folio).param("ts", ts).query(Long.class).optional();
    }

    public void usarRecuperacion(String folio) {
        jdbc.sql("UPDATE core_password_recovery SET used = TRUE WHERE folio = :folio").param("folio", folio).update();
    }

    /** Usuarios semilla sin contraseña (los de las migraciones). */
    public List<Long> sinPassword() {
        return jdbc.sql("SELECT id FROM core_users WHERE password_hash IS NULL").query(Long.class).list();
    }

    public void fijarHash(long userId, String hash) {
        jdbc.sql("UPDATE core_users SET password_hash = :hash WHERE id = :id")
                .param("hash", hash).param("id", userId).update();
    }

    private static final String SELECT = """
            SELECT id, username, email, password_hash, area, active, principal, is_verified, is_password
            FROM core_users""";

    private Cuenta map(java.sql.ResultSet row, int n) throws java.sql.SQLException {
        return new Cuenta(row.getLong("id"), row.getString("username"), row.getString("email"),
                row.getString("password_hash"), (Integer) row.getObject("area"), row.getBoolean("active"),
                row.getString("principal"), row.getBoolean("is_verified"), row.getBoolean("is_password"));
    }
}
