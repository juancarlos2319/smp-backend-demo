package mx.gob.seph.smp.demo.auth;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/** `data` del login: lo que el front guarda como sesión. */
public record SessionData(
        long userId,
        List<Integer> roles,
        Map<String, Boolean> permisos,
        boolean verified,
        @JsonProperty("isPassword") boolean isPassword,
        UserConfig config) {

    public record UserConfig(String principal, List<Integer> extras) {
    }
}
