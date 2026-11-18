package mx.gob.seph.smp.demo.auth;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import mx.gob.seph.smp.demo.api.ApiResponse;
import mx.gob.seph.smp.demo.cuenta.CuentaService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Login y operaciones de la propia cuenta. Los datos de cuenta llegan en headers, como en el back real. */
@RestController
public class AuthController {
    private final CuentaService cuentaService;

    public AuthController(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<SessionData>> login(@Valid @RequestBody LoginRequest request) {
        return cuentaService.login(request.user(), request.password())
                .map(login -> ResponseEntity.ok()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.token())
                        .body(ApiResponse.success(login.session())))
                .orElseGet(() -> ResponseEntity.status(401)
                        .body(ApiResponse.failure("Usuario o contraseña incorrectos.")));
    }

    @PostMapping("/users/validateToken")
    public ApiResponse<Void> validateToken(
            @AuthenticationPrincipal AuthenticatedDemoSession session,
            @RequestHeader(name = "token", required = false) String code) {
        return cuentaService.verificar(session.userId(), code);
    }

    @PostMapping("/users/changePassword")
    public ApiResponse<Map<String, String>> changePassword(
            @AuthenticationPrincipal AuthenticatedDemoSession session,
            @RequestHeader("password") String password) {
        return cuentaService.cambiarPassword(session.userId(), password);
    }

    @GetMapping("/users/me")
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal AuthenticatedDemoSession session) {
        return ApiResponse.success(cuentaService.me(session.userId()));
    }

    @PostMapping("/users/sendChangePassword")
    public ApiResponse<Void> sendChangePassword(@RequestHeader(name = "username", required = false) String username) {
        return cuentaService.enviarLiga(username);
    }

    @GetMapping("/users/validatePasswordRecovery")
    public ApiResponse<Void> validatePasswordRecovery(
            @RequestHeader("token") String folio,
            @RequestHeader("ts") String ts) {
        return cuentaService.validarLiga(folio, ts);
    }

    @PostMapping("/users/changePasswordRecovery")
    public ApiResponse<Void> changePasswordRecovery(
            @RequestHeader("token") String folio,
            @RequestHeader("ts") String ts,
            @RequestHeader("password") String password) {
        return cuentaService.recuperarPassword(folio, ts, password);
    }

    public record LoginRequest(@NotBlank String user, @NotBlank String password) {
    }
}
