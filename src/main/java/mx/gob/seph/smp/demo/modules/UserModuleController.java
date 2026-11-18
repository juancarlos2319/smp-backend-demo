package mx.gob.seph.smp.demo.modules;

import java.util.List;

import mx.gob.seph.smp.demo.api.ApiResponse;
import mx.gob.seph.smp.demo.auth.AuthenticatedDemoSession;
import mx.gob.seph.smp.demo.auth.JwtSessionService;
import mx.gob.seph.smp.demo.seguridad.PermisosService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserModuleController {
    private final PermisosService permisosService;
    private final JwtSessionService jwtSessionService;

    public UserModuleController(PermisosService permisosService, JwtSessionService jwtSessionService) {
        this.permisosService = permisosService;
        this.jwtSessionService = jwtSessionService;
    }

    /** Módulos de los roles y extras del usuario, con sus ancestros y sus banderas. */
    @GetMapping("/users/moduleByUser")
    public ApiResponse<List<UserModule>> modules(@AuthenticationPrincipal AuthenticatedDemoSession session) {
        List<UserModule> modules = permisosService.modulos(session.userId()).stream()
                .map(m -> new UserModule(m.id(), m.name(), m.description(), m.path(), m.path(), m.parentId(),
                        m.parentName(), m.icon(), m.materialIcon(), m.vista(), m.codigo(), m.permisosJson(),
                        m.orden()))
                .toList();
        return ApiResponse.success(modules, modules.size());
    }

    /** Invalida todos los tokens del usuario, en todos sus dispositivos. */
    @PostMapping("/users/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthenticatedDemoSession session) {
        jwtSessionService.revokeAll(session.userId());
        return ApiResponse.success(null);
    }
}
