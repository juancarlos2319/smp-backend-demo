package mx.gob.seph.smp.demo.roles;

import java.util.List;
import java.util.Map;

import mx.gob.seph.smp.demo.api.ApiResponse;
import mx.gob.seph.smp.demo.auth.AuthenticatedDemoSession;
import mx.gob.seph.smp.demo.roles.RolesService.RoleDetail;
import mx.gob.seph.smp.demo.roles.RolesService.RolEtapa;
import mx.gob.seph.smp.demo.roles.RolesService.RolePayload;
import mx.gob.seph.smp.demo.roles.RolesService.RoleRow;
import mx.gob.seph.smp.demo.seguridad.Permiso;
import mx.gob.seph.smp.demo.seguridad.PermisosService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** `/roles`: el id del rol va en el header `roleId`, como en el back real. */
@RestController
public class RolesController {
    private static final String MODULO = "GESTION_ROLES";

    private final RolesService service;
    private final PermisosService permisos;

    public RolesController(RolesService service, PermisosService permisos) {
        this.service = service;
        this.permisos = permisos;
    }

    /** Lo permite cualquiera de los módulos de Gestión (la pantalla de usuarios lo usa para elegir roles). */
    @GetMapping("/roles")
    public ApiResponse<List<RoleRow>> list(@AuthenticationPrincipal AuthenticatedDemoSession session) {
        permisos.requiereAlguno(session.userId(), "GESTION_USUARIOS", MODULO, "GESTION_MODULOS");
        List<RoleRow> rows = service.list();
        return ApiResponse.success(rows, rows.size());
    }

    @GetMapping("/roles/role")
    public ApiResponse<RoleDetail> get(
            @AuthenticationPrincipal AuthenticatedDemoSession session, @RequestHeader("roleId") int roleId) {
        permisos.requiere(session.userId(), MODULO);
        return ApiResponse.success(service.get(roleId));
    }

    @PostMapping("/roles")
    public ApiResponse<Map<String, Object>> create(
            @AuthenticationPrincipal AuthenticatedDemoSession session, @RequestBody RolePayload payload) {
        permisos.requiere(session.userId(), MODULO, Permiso.AGREGAR);
        return ApiResponse.ok("Se agregó el rol.", service.create(payload));
    }

    @PatchMapping("/roles")
    public ApiResponse<Void> update(
            @AuthenticationPrincipal AuthenticatedDemoSession session,
            @RequestHeader("roleId") int roleId,
            @RequestBody RolePayload payload) {
        permisos.requiere(session.userId(), MODULO, Permiso.EDITAR);
        service.update(roleId, payload);
        return ApiResponse.ok("Se actualizó el rol.", null);
    }

    @PatchMapping("/roles/softdeleted")
    public ApiResponse<Void> remove(
            @AuthenticationPrincipal AuthenticatedDemoSession session, @RequestHeader("roleId") int roleId) {
        permisos.requiere(session.userId(), MODULO, Permiso.ELIMINAR);
        service.remove(roleId);
        return ApiResponse.ok("Se eliminó el rol.", null);
    }

    @GetMapping("/roles/etapas")
    public ApiResponse<List<RolEtapa>> etapas(
            @AuthenticationPrincipal AuthenticatedDemoSession session, @RequestHeader("roleId") int roleId) {
        permisos.requiere(session.userId(), MODULO);
        return ApiResponse.success(service.etapas(roleId));
    }

    @PutMapping("/roles/etapas")
    public ApiResponse<List<RolEtapa>> updateEtapas(
            @AuthenticationPrincipal AuthenticatedDemoSession session,
            @RequestHeader("roleId") int roleId,
            @RequestBody List<String> etapas) {
        permisos.requiere(session.userId(), MODULO, Permiso.EDITAR);
        return ApiResponse.ok("Se actualizaron las etapas del rol.", service.reemplazarEtapas(roleId, etapas));
    }
}
