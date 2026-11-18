package mx.gob.seph.smp.demo.usuarios;

import java.util.List;
import java.util.Map;

import mx.gob.seph.smp.demo.api.ApiResponse;
import mx.gob.seph.smp.demo.auth.AuthenticatedDemoSession;
import mx.gob.seph.smp.demo.seguridad.Permiso;
import mx.gob.seph.smp.demo.seguridad.PermisosService;
import mx.gob.seph.smp.demo.usuarios.UsuariosService.NivelRh;
import mx.gob.seph.smp.demo.usuarios.UsuariosService.UserDetail;
import mx.gob.seph.smp.demo.usuarios.UsuariosService.UserPayload;
import mx.gob.seph.smp.demo.usuarios.UsuariosService.UserRow;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** `/users`: el id del usuario a consultar o modificar va en el header `userId`, como en el back real. */
@RestController
public class UsuariosController {
    private static final String MODULO = "GESTION_USUARIOS";

    private final UsuariosService service;
    private final PermisosService permisos;

    public UsuariosController(UsuariosService service, PermisosService permisos) {
        this.service = service;
        this.permisos = permisos;
    }

    @GetMapping("/users")
    public ApiResponse<List<UserRow>> list(@AuthenticationPrincipal AuthenticatedDemoSession session) {
        permisos.requiere(session.userId(), MODULO);
        List<UserRow> rows = service.list();
        return ApiResponse.success(rows, rows.size());
    }

    @GetMapping("/users/niveles")
    public ApiResponse<List<NivelRh>> niveles(@AuthenticationPrincipal AuthenticatedDemoSession session) {
        permisos.requiere(session.userId(), MODULO);
        return ApiResponse.success(service.niveles());
    }

    @GetMapping("/users/user")
    public ApiResponse<UserDetail> get(
            @AuthenticationPrincipal AuthenticatedDemoSession session, @RequestHeader("userId") long userId) {
        permisos.requiere(session.userId(), MODULO);
        return ApiResponse.success(service.get(userId));
    }

    @PostMapping("/users")
    public ApiResponse<Map<String, Object>> create(
            @AuthenticationPrincipal AuthenticatedDemoSession session, @RequestBody UserPayload payload) {
        permisos.requiere(session.userId(), MODULO, Permiso.AGREGAR);
        return ApiResponse.ok("Se agregó el usuario.", service.create(payload));
    }

    @PatchMapping("/users")
    public ApiResponse<Void> update(
            @AuthenticationPrincipal AuthenticatedDemoSession session,
            @RequestHeader("userId") long userId,
            @RequestBody UserPayload payload) {
        permisos.requiere(session.userId(), MODULO, Permiso.EDITAR);
        service.update(userId, payload);
        return ApiResponse.ok("Se actualizó el usuario.", null);
    }

    @PatchMapping("/users/softdeleted")
    public ApiResponse<Void> remove(
            @AuthenticationPrincipal AuthenticatedDemoSession session, @RequestHeader("userId") long userId) {
        permisos.requiere(session.userId(), MODULO, Permiso.ELIMINAR);
        service.remove(userId, session.userId());
        return ApiResponse.ok("Se eliminó el usuario.", null);
    }
}
