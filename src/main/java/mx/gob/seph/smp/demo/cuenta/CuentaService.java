package mx.gob.seph.smp.demo.cuenta;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import mx.gob.seph.smp.demo.api.ApiResponse;
import mx.gob.seph.smp.demo.auth.JwtSessionService;
import mx.gob.seph.smp.demo.auth.SessionData;
import mx.gob.seph.smp.demo.cuenta.CuentaRepository.Cuenta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login y cuenta propia. El demo no envía correos: el código de verificación y la liga de recuperación
 * se escriben en el log (y `demo.mail.codigo-fijo`, si tiene valor, reemplaza al código aleatorio).
 */
@Service
public class CuentaService {
    private static final Logger log = LoggerFactory.getLogger(CuentaService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HOME = "/dashboard";

    /** Resultado del login: la sesión y el token, o vacío si las credenciales no son válidas. */
    public record Login(SessionData session, String token) {
    }

    private final CuentaRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtSessionService jwtSessionService;
    private final String codigoFijo;
    private final String recuperacionUrl;

    public CuentaService(
            CuentaRepository repository,
            PasswordEncoder passwordEncoder,
            JwtSessionService jwtSessionService,
            @Value("${demo.mail.codigo-fijo:}") String codigoFijo,
            @Value("${demo.front.recuperacion-url}") String recuperacionUrl) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtSessionService = jwtSessionService;
        this.codigoFijo = codigoFijo;
        this.recuperacionUrl = recuperacionUrl;
    }

    @Transactional
    public Optional<Login> login(String username, String password) {
        Optional<Cuenta> found = repository.porUsername(username.trim());
        if (found.isEmpty() || !found.get().active() || found.get().passwordHash() == null
                || !passwordEncoder.matches(password, found.get().passwordHash())) {
            return Optional.empty();
        }
        Cuenta cuenta = found.get();
        if (!cuenta.verified()) {
            enviarCodigo(cuenta);
        }
        String token = jwtSessionService.createToken(cuenta.username(), cuenta.id());
        return Optional.of(new Login(session(cuenta), token));
    }

    /** Confirma el código del correo; el front lo manda en el header `token`. */
    @Transactional
    public ApiResponse<Void> verificar(long userId, String code) {
        if (code == null || !repository.codigoValido(userId, code.trim())) {
            return ApiResponse.failure("El código es inválido o ya expiró.");
        }
        repository.marcarVerificado(userId);
        return ApiResponse.ok("Cuenta verificada", null);
    }

    /** Cambio obligatorio: solo una vez. Invalida todos los tokens y regresa uno nuevo en `data.token`. */
    @Transactional
    public ApiResponse<Map<String, String>> cambiarPassword(long userId, String password) {
        Cuenta cuenta = repository.porId(userId).orElseThrow();
        if (cuenta.passwordSet()) {
            return ApiResponse.failure("La contraseña ya se cambió; usa la recuperación de contraseña.");
        }
        if (!passwordValida(password)) {
            return ApiResponse.failure("La contraseña debe tener al menos 8 caracteres.");
        }
        repository.cambiarPassword(userId, passwordEncoder.encode(password));
        jwtSessionService.revokeAll(userId);
        String token = jwtSessionService.createToken(cuenta.username(), userId);
        return ApiResponse.ok("Contraseña actualizada", Map.of("token", token));
    }

    public Map<String, Object> me(long userId) {
        Cuenta cuenta = repository.porId(userId).orElseThrow();
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("id", cuenta.id());
        data.put("username", cuenta.username());
        data.put("email", cuenta.email());
        data.put("area", cuenta.area());
        data.put("principal", cuenta.principal());
        return data;
    }

    /** Siempre responde lo mismo, exista o no el usuario. */
    @Transactional
    public ApiResponse<Void> enviarLiga(String username) {
        repository.porUsername(username == null ? "" : username.trim())
                .filter(Cuenta::active)
                .ifPresent(cuenta -> {
                    String folio = UUID.randomUUID().toString().replace("-", "");
                    String ts = String.valueOf(System.currentTimeMillis());
                    repository.guardarRecuperacion(folio, ts, cuenta.id(), LocalDateTime.now().plusMinutes(15));
                    log.info("[DEMO] Liga de recuperación para {}: {}?folio={}&ts={}",
                            cuenta.username(), recuperacionUrl, folio, ts);
                });
        return ApiResponse.ok("Si el usuario existe, se envió una liga a su correo.", null);
    }

    public ApiResponse<Void> validarLiga(String folio, String ts) {
        return repository.recuperacionVigente(folio, ts).isPresent()
                ? ApiResponse.ok("OK", null)
                : ApiResponse.failure("La liga no es válida o ya expiró.");
    }

    @Transactional
    public ApiResponse<Void> recuperarPassword(String folio, String ts, String password) {
        Optional<Long> userId = repository.recuperacionVigente(folio, ts);
        if (userId.isEmpty()) {
            return ApiResponse.failure("La liga no es válida o ya expiró.");
        }
        if (!passwordValida(password)) {
            return ApiResponse.failure("La contraseña debe tener al menos 8 caracteres.");
        }
        repository.cambiarPassword(userId.get(), passwordEncoder.encode(password));
        repository.usarRecuperacion(folio);
        jwtSessionService.revokeAll(userId.get());
        return ApiResponse.ok("Contraseña actualizada", null);
    }

    private SessionData session(Cuenta cuenta) {
        boolean pendiente = !cuenta.verified() || !cuenta.passwordSet();
        // Con un paso pendiente el back real manda una ruta del front anterior; el front la ignora
        String principal = pendiente || cuenta.principal() == null ? HOME : cuenta.principal();
        return new SessionData(
                cuenta.id(),
                repository.roles(cuenta.id()),
                Map.of(),
                cuenta.verified(),
                cuenta.passwordSet(),
                new SessionData.UserConfig(principal, repository.extras(cuenta.id())));
    }

    private void enviarCodigo(Cuenta cuenta) {
        String code = codigoFijo == null || codigoFijo.isBlank()
                ? String.format("%06d", RANDOM.nextInt(1_000_000))
                : codigoFijo.trim();
        repository.guardarCodigo(cuenta.id(), code, LocalDateTime.now().plusMinutes(5));
        log.info("[DEMO] Código de verificación para {}: {}", cuenta.username(), code);
    }

    private static boolean passwordValida(String password) {
        return password != null && password.length() >= 8;
    }
}
