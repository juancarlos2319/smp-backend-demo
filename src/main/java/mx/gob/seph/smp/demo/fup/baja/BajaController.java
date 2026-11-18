package mx.gob.seph.smp.demo.fup.baja;

import java.util.List;

import mx.gob.seph.smp.demo.api.ApiResponse;
import mx.gob.seph.smp.demo.auth.AuthenticatedDemoSession;
import mx.gob.seph.smp.demo.fup.baja.BajaService.BajaFolioResultado;
import mx.gob.seph.smp.demo.fup.baja.BajaService.FolioBaja;
import mx.gob.seph.smp.demo.fup.baja.BajaService.FolioBajaResumen;
import mx.gob.seph.smp.demo.seguridad.Permiso;
import mx.gob.seph.smp.demo.seguridad.PermisosService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fup/movimientos/baja")
public class BajaController {
    private final BajaService service;
    private final PermisosService permisos;

    public BajaController(BajaService service, PermisosService permisos) {
        this.service = service;
        this.permisos = permisos;
    }

    @GetMapping
    public ApiResponse<List<FolioBajaResumen>> buscar(
            @AuthenticationPrincipal AuthenticatedDemoSession session,
            @RequestParam(required = false) String rfc,
            @RequestParam(required = false) String plaza,
            @RequestParam(required = false) Integer limit) {
        permisos.requiere(session.userId(), BajaService.MODULO);
        List<FolioBajaResumen> data = service.buscar(rfc, plaza, limit);
        return ApiResponse.success(data, data.size());
    }

    @GetMapping("/{acuerdo}")
    public ApiResponse<FolioBaja> folio(
            @AuthenticationPrincipal AuthenticatedDemoSession session, @PathVariable int acuerdo) {
        permisos.requiere(session.userId(), BajaService.MODULO);
        return ApiResponse.success(service.folio(session.userId(), acuerdo));
    }

    @DeleteMapping("/{acuerdo}")
    public ApiResponse<BajaFolioResultado> darDeBaja(
            @AuthenticationPrincipal AuthenticatedDemoSession session, @PathVariable int acuerdo) {
        permisos.requiere(session.userId(), BajaService.MODULO, Permiso.ELIMINAR);
        BajaFolioResultado resultado = service.darDeBaja(session.userId(), acuerdo);
        String empleado = resultado.empleadoBorrado() ? " y el empleado que creó" : "";
        return ApiResponse.ok("Se dio de baja el folio " + acuerdo + empleado + "; se restauraron "
                + resultado.plazasRestauradas() + " plaza(s).", resultado);
    }
}
