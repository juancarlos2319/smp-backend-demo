package mx.gob.seph.smp.demo.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * JWT HS256 firmado por el demo. Los tokens vigentes se guardan en memoria para poder revocarlos:
 * `/users/logout` y el cambio de contraseña invalidan todos los del usuario, como el back real.
 */
@Service
public class JwtSessionService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> CLAIMS_TYPE = new TypeReference<>() {
    };

    /** Resultado de validar un token. */
    public sealed interface Validation permits Valid, Revoked, Invalid {
    }

    public record Valid(AuthenticatedDemoSession session) implements Validation {
    }

    /** El token era válido pero se cerró la sesión: el 401 lleva "Sesión cerrada". */
    public record Revoked() implements Validation {
    }

    public record Invalid() implements Validation {
    }

    private record ActiveToken(long userId, String username, Instant expiresAt) {
    }

    private final ObjectMapper objectMapper;
    private final SecretKeySpec signingKey;
    private final Duration tokenLifetime;
    private final ConcurrentHashMap<String, ActiveToken> activeTokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> revokedTokens = new ConcurrentHashMap<>();

    public JwtSessionService(
            ObjectMapper objectMapper,
            @Value("${demo.security.jwt-secret}") String secret,
            @Value("${demo.security.token-lifetime}") Duration tokenLifetime) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("La clave JWT debe tener al menos 32 bytes.");
        }
        if (tokenLifetime.isZero() || tokenLifetime.isNegative()) {
            throw new IllegalArgumentException("La vigencia del token debe ser positiva.");
        }
        this.objectMapper = objectMapper;
        this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.tokenLifetime = tokenLifetime;
    }

    public String createToken(String username, long userId) {
        Instant now = Instant.now();
        Instant expiresAt = Instant.ofEpochSecond(now.plus(tokenLifetime).getEpochSecond());
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> claims = Map.of(
                "sub", username,
                "userId", userId,
                "jti", UUID.randomUUID().toString(),
                "iat", now.getEpochSecond(),
                "exp", expiresAt.getEpochSecond());
        String unsignedToken = encode(header) + "." + encode(claims);
        String token = unsignedToken + "." + ENCODER.encodeToString(signForCreation(unsignedToken));
        purgeExpired(now);
        activeTokens.put(token, new ActiveToken(userId, username, expiresAt));
        return token;
    }

    public Validation validate(String token) {
        ActiveToken active = activeTokens.get(token);
        if (active == null) {
            return revokedTokens.containsKey(token) ? new Revoked() : new Invalid();
        }
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                return new Invalid();
            }
            String unsignedToken = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(sign(unsignedToken), DECODER.decode(parts[2]))) {
                return new Invalid();
            }
            Map<String, Object> header = objectMapper.readValue(DECODER.decode(parts[0]), CLAIMS_TYPE);
            Map<String, Object> claims = objectMapper.readValue(DECODER.decode(parts[1]), CLAIMS_TYPE);
            if (!"HS256".equals(header.get("alg"))
                    || !(claims.get("exp") instanceof Number expiration)
                    || Instant.ofEpochSecond(expiration.longValue()).isBefore(Instant.now())
                    || !active.expiresAt().equals(Instant.ofEpochSecond(expiration.longValue()))) {
                activeTokens.remove(token);
                return new Invalid();
            }
            return new Valid(new AuthenticatedDemoSession(active.userId(), active.username(), token));
        } catch (IllegalArgumentException | GeneralSecurityException | JacksonException exception) {
            return new Invalid();
        }
    }

    /** Cierra todas las sesiones del usuario, en todos sus dispositivos. */
    public void revokeAll(long userId) {
        activeTokens.entrySet().removeIf(entry -> {
            if (entry.getValue().userId() != userId) {
                return false;
            }
            revokedTokens.put(entry.getKey(), entry.getValue().expiresAt());
            return true;
        });
    }

    private void purgeExpired(Instant now) {
        activeTokens.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        revokedTokens.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }

    private String encode(Object value) {
        try {
            return ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JacksonException exception) {
            throw new IllegalStateException("No se pudo crear el token de sesión.", exception);
        }
    }

    private byte[] sign(String value) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(signingKey);
        return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    }

    private byte[] signForCreation(String value) {
        try {
            return sign(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo firmar el token de sesión.", exception);
        }
    }
}
