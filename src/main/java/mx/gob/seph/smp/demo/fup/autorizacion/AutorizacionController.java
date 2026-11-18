package mx.gob.seph.smp.demo.fup.autorizacion;

import java.util.List;

import mx.gob.seph.smp.demo.api.ApiResponse;
import mx.gob.seph.smp.demo.auth.AuthenticatedDemoSession;
import mx.gob.seph.smp.demo.fup.autorizacion.AutorizacionService.Detalle;
import mx.gob.seph.smp.demo.fup.autorizacion.AutorizacionService.EtapasAutorizacion;
import mx.gob.seph.smp.demo.fup.autorizacion.AutorizacionService.MotivoRechazo;
import mx.gob.seph.smp.demo.fup.autorizacion.AutorizacionService.Pendientes;
import mx.gob.seph.smp.demo.fup.autorizacion.AutorizacionService.RechazoRequest;
import mx.gob.seph.smp.demo.seguridad.PermisosService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bandeja en `/fup/movimientos/autorizacion`; aprobar y rechazar en `/fup/movimientos/{acuerdo}/...`. */
@RestController
public class AutorizacionController {
    private static final String BASE = "/fup/movimientos/autorizacion";

    private final AutorizacionService service;
    private final PermisosService permisos;

    public AutorizacionController(AutorizacionService service, PermisosService permisos) {
        this.service = service;
        this.permisos = permisos;
    }

    @GetMapping(BASE + "/etapas")
    public ApiResponse<EtapasAutorizacion> etapas(@AuthenticationPrincipal AuthenticatedDemoSession session) {
        requiere(session);
        return ApiResponse.success(service.etapas(session.userId()));
    }

    @GetMapping(BASE + "/pendientes")
    public ApiResponse<Pendientes> pendientes(
            @AuthenticationPrincipal AuthenticatedDemoSession session,
            @RequestParam String etapa,
            @RequestParam(required = false) String acuerdo,
            @RequestParam(required = false) String rfc,
            @RequestParam(required = false) String plaza,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        requiere(session);
        return ApiResponse.success(service.pendientes(session.userId(), etapa, acuerdo, rfc, plaza, limit, offset));
    }

    @GetMapping(BASE + "/motivos-rechazo")
    public ApiResponse<List<MotivoRechazo>> motivosRechazo(@AuthenticationPrincipal AuthenticatedDemoSession session) {
        requiere(session);
        return ApiResponse.success(service.motivosRechazo());
    }

    @GetMapping(BASE + "/{acuerdo}")
    public ApiResponse<Detalle> detalle(
            @AuthenticationPrincipal AuthenticatedDemoSession session, @PathVariable int acuerdo) {
        requiere(session);
        return ApiResponse.success(service.detalle(session.userId(), acuerdo));
    }

    @PostMapping("/fup/movimientos/{acuerdo}/aprobar/{etapa}")
    public ApiResponse<Void> aprobar(
            @AuthenticationPrincipal AuthenticatedDemoSession session,
            @PathVariable int acuerdo,
            @PathVariable String etapa) {
        requiere(session);
        String mensaje = service.aprobar(session.userId(), acuerdo, etapa);
        return ApiResponse.ok(mensaje, null);
    }

    @PostMapping("/fup/movimientos/{acuerdo}/rechazar")
    public ApiResponse<Void> rechazar(
            @AuthenticationPrincipal AuthenticatedDemoSession session,
            @PathVariable int acuerdo,
            @RequestBody RechazoRequest body) {
        requiere(session);
        service.rechazar(session.userId(), acuerdo, body);
        return ApiResponse.ok("Se rechazó el folio " + acuerdo + "; regresó a Registro.", null);
    }

    /** Ver la bandeja pide el módulo; aprobar pide además AUTORIZAR en los movimientos (lo revisa el servicio). */
    private void requiere(AuthenticatedDemoSession session) {
        permisos.requiere(session.userId(), AutorizacionService.MODULO);
    }
}
