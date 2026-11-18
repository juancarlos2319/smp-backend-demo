package mx.gob.seph.smp.demo.modulos;

import java.util.List;
import java.util.Map;

import mx.gob.seph.smp.demo.api.ApiResponse;
import mx.gob.seph.smp.demo.auth.AuthenticatedDemoSession;
import mx.gob.seph.smp.demo.modulos.ModulosService.CoreIcon;
import mx.gob.seph.smp.demo.modulos.ModulosService.ModuleDetail;
import mx.gob.seph.smp.demo.modulos.ModulosService.ModuleOption;
import mx.gob.seph.smp.demo.modulos.ModulosService.ModulePayload;
import mx.gob.seph.smp.demo.seguridad.Permiso;
import mx.gob.seph.smp.demo.seguridad.PermisosService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** `/modules` e `/icons`: el id del módulo va en el header `moduleId`, como en el back real. */
@RestController
public class ModulosController {
    private static final String MODULO = "GESTION_MODULOS";

    private final ModulosService service;
    private final PermisosService permisos;

    public ModulosController(ModulosService service, PermisosService permisos) {
        this.service = service;
        this.permisos = permisos;
    }

    /** Lo permite cualquiera de los módulos de Gestión (usuarios y roles eligen módulos de aquí). */
    @GetMapping("/modules")
    public ApiResponse<List<ModuleOption>> list(@AuthenticationPrincipal AuthenticatedDemoSession session) {
        permisos.requiereAlguno(session.userId(), "GESTION_USUARIOS", "GESTION_ROLES", MODULO);
        List<ModuleOption> rows = service.list();
        return ApiResponse.success(rows, rows.size());
    }

    @GetMapping("/icons")
    public ApiResponse<List<CoreIcon>> icons(@AuthenticationPrincipal AuthenticatedDemoSession session) {
        permisos.requiere(session.userId(), MODULO);
        return ApiResponse.success(service.icons());
    }

    @GetMapping("/modules/module")
    public ApiResponse<ModuleDetail> get(
            @AuthenticationPrincipal AuthenticatedDemoSession session, @RequestHeader("moduleId") int moduleId) {
        permisos.requiere(session.userId(), MODULO);
        return ApiResponse.success(service.get(moduleId));
    }

    @PostMapping("/modules")
    public ApiResponse<Map<String, Object>> create(
            @AuthenticationPrincipal AuthenticatedDemoSession session, @RequestBody ModulePayload payload) {
        permisos.requiere(session.userId(), MODULO, Permiso.AGREGAR);
        return ApiResponse.ok("Se agregó el módulo.", service.create(payload));
    }

    @PatchMapping("/modules")
    public ApiResponse<Map<String, Object>> update(
            @AuthenticationPrincipal AuthenticatedDemoSession session,
            @RequestHeader("moduleId") int moduleId,
            @RequestBody ModulePayload payload) {
        permisos.requiere(session.userId(), MODULO, Permiso.EDITAR);
        service.update(moduleId, payload);
        return ApiResponse.ok("Se actualizó el módulo.", Map.of("id", moduleId));
    }

    @PatchMapping("/modules/softdeleted")
    public ApiResponse<Void> remove(
            @AuthenticationPrincipal AuthenticatedDemoSession session, @RequestHeader("moduleId") int moduleId) {
        permisos.requiere(session.userId(), MODULO, Permiso.ELIMINAR);
        int total = service.remove(moduleId);
        return ApiResponse.ok(total == 1 ? "Se eliminó el módulo." : "Se eliminaron " + total + " módulos.", null);
    }
}
