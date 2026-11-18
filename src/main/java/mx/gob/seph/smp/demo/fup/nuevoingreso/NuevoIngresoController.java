package mx.gob.seph.smp.demo.fup.nuevoingreso;

import java.util.List;

import mx.gob.seph.smp.demo.api.ApiResponse;
import mx.gob.seph.smp.demo.auth.AuthenticatedDemoSession;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.Busqueda;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.Catalogos;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.CentroTrabajoNuevoIngreso;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.ClaveOpcion;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.NuevoIngresoRequest;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.PlazaNuevoIngreso;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.Resultado;
import mx.gob.seph.smp.demo.seguridad.PermisosService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/fup/movimientos/nuevo-ingreso")
public class NuevoIngresoController {
    private final NuevoIngresoService service;
    private final PermisosService permisos;

    public NuevoIngresoController(NuevoIngresoService service, PermisosService permisos) {
        this.service = service;
        this.permisos = permisos;
    }

    @GetMapping("/catalogos")
    public ApiResponse<Catalogos> catalogos(@AuthenticationPrincipal AuthenticatedDemoSession session) {
        requiere(session);
        return ApiResponse.success(service.catalogos(session.userId()));
    }

    @GetMapping("/municipios")
    public ApiResponse<List<ClaveOpcion>> municipios(
            @AuthenticationPrincipal AuthenticatedDemoSession session, @RequestParam String cveEnt) {
        requiere(session);
        return ApiResponse.success(service.municipios(cveEnt));
    }

    @GetMapping("/localidades")
    public ApiResponse<List<ClaveOpcion>> localidades(
            @AuthenticationPrincipal AuthenticatedDemoSession session,
            @RequestParam String cveEnt,
            @RequestParam String cveMun) {
        requiere(session);
        return ApiResponse.success(service.localidades(cveEnt, cveMun));
    }

    @GetMapping("/plazas")
    public ApiResponse<List<PlazaNuevoIngreso>> plazas(
            @AuthenticationPrincipal AuthenticatedDemoSession session,
            @RequestParam(defaultValue = "") String clave,
            @RequestParam(required = false) Integer limit) {
        requiere(session);
        List<PlazaNuevoIngreso> data = service.plazas(clave, limit);
        return ApiResponse.success(data, data.size());
    }

    @GetMapping("/centros-trabajo")
    public ApiResponse<List<CentroTrabajoNuevoIngreso>> centrosTrabajo(
            @AuthenticationPrincipal AuthenticatedDemoSession session,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Integer limit) {
        requiere(session);
        List<CentroTrabajoNuevoIngreso> data = service.centrosTrabajo(q, limit);
        return ApiResponse.success(data, data.size());
    }

    @GetMapping("/busqueda")
    public ApiResponse<Busqueda> busqueda(
            @AuthenticationPrincipal AuthenticatedDemoSession session,
            @RequestParam(required = false) String rfc,
            @RequestParam(required = false) String curp) {
        requiere(session);
        return ApiResponse.success(service.buscar(session.userId(), rfc, curp));
    }

    /** Multipart: `datos` (JSON) y `clabePdf` (opcional). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Resultado> registrar(
            @AuthenticationPrincipal AuthenticatedDemoSession session,
            @RequestPart("datos") NuevoIngresoRequest datos,
            @RequestPart(name = "clabePdf", required = false) MultipartFile clabePdf) {
        requiere(session);
        Resultado resultado = service.registrar(session.userId(), datos, clabePdf);
        return ApiResponse.ok("Se registró el folio " + resultado.acuerdo() + ".", resultado);
    }

    private void requiere(AuthenticatedDemoSession session) {
        permisos.requiere(session.userId(), NuevoIngresoService.MODULO);
    }
}
