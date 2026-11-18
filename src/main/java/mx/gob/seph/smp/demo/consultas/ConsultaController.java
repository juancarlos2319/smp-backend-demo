package mx.gob.seph.smp.demo.consultas;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import mx.gob.seph.smp.demo.api.ApiResponse;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.Categoria;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.CategoriaConcepto;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.CentroTrabajoDetalle;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.CentroTrabajoEstatus;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.CentroTrabajoPlaza;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.CentroTrabajoResumen;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.CompatibilidadEmpleo;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.Concepto;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.EmpleadoDatos;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.EmpleadoOpcion;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.EmpleadoPlaza;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.EmpleadoResumen;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.HistorialMovimiento;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.PlazaDetalle;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.PlazaOcupante;
import mx.gob.seph.smp.demo.consultas.ConsultaDemoService.PlazaResumen;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class ConsultaController {
    private final ConsultaDemoService service;

    public ConsultaController(ConsultaDemoService service) {
        this.service = service;
    }

    @GetMapping("/empleados/opciones")
    public ApiResponse<List<EmpleadoOpcion>> opcionesEmpleados(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            @RequestParam(defaultValue = "false") boolean incluirInactivos) {
        List<EmpleadoOpcion> data = service.opcionesEmpleados(q, limit, incluirInactivos);
        return ApiResponse.success(data, data.size());
    }

    @GetMapping("/consultas/empleados")
    public ApiResponse<List<EmpleadoResumen>> empleados(
            @RequestParam(defaultValue = "") String rfc,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        List<EmpleadoResumen> data = service.buscarEmpleados(rfc, limit);
        return ApiResponse.success(data, data.size());
    }

    @GetMapping("/consultas/empleados/{id}")
    public ApiResponse<EmpleadoDatos> empleado(@PathVariable int id) {
        return ApiResponse.success(service.empleado(id));
    }

    @GetMapping("/consultas/empleados/{id}/plazas")
    public ApiResponse<List<EmpleadoPlaza>> plazasEmpleado(@PathVariable int id) {
        List<EmpleadoPlaza> data = service.plazasDeEmpleado(id);
        return ApiResponse.success(data, data.size());
    }

    @GetMapping("/consultas/empleados/{id}/historial")
    public ApiResponse<List<HistorialMovimiento>> historialEmpleado(@PathVariable int id) {
        List<HistorialMovimiento> data = service.historialDeEmpleado(id);
        return ApiResponse.success(data, data.size());
    }

    @GetMapping("/consultas/compatibilidad/{empleadoId}")
    public ApiResponse<CompatibilidadEmpleo> compatibilidad(@PathVariable int empleadoId) {
        return ApiResponse.success(service.compatibilidad(empleadoId));
    }

    @GetMapping("/consultas/plazas")
    public ApiResponse<List<PlazaResumen>> plazas(
            @RequestParam(defaultValue = "") String clave,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        List<PlazaResumen> data = service.buscarPlazas(clave, limit);
        return ApiResponse.success(data, data.size());
    }

    @GetMapping("/consultas/plazas/{id}")
    public ApiResponse<PlazaDetalle> plaza(@PathVariable int id) {
        return ApiResponse.success(service.plaza(id));
    }

    @GetMapping("/consultas/plazas/{id}/ocupantes")
    public ApiResponse<List<PlazaOcupante>> ocupantes(@PathVariable int id) {
        List<PlazaOcupante> data = service.ocupantes(id);
        return ApiResponse.success(data, data.size());
    }

    @GetMapping("/consultas/plazas/{id}/historial")
    public ApiResponse<List<HistorialMovimiento>> historialPlaza(
            @PathVariable int id, @RequestParam(required = false) String cct) {
        List<HistorialMovimiento> data = service.historialDePlaza(id, cct);
        return ApiResponse.success(data, data.size());
    }

    @GetMapping("/consultas/centros-trabajo")
    public ApiResponse<List<CentroTrabajoResumen>> centrosTrabajo(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) @Min(0) @Max(1) Integer estatus,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        List<CentroTrabajoResumen> data = service.buscarCentros(q, estatus, limit);
        return ApiResponse.success(data, data.size());
    }

    @GetMapping("/consultas/centros-trabajo/{clave}")
    public ApiResponse<CentroTrabajoDetalle> centroTrabajo(@PathVariable String clave) {
        return ApiResponse.success(service.centroTrabajo(clave));
    }

    @GetMapping("/consultas/centros-trabajo/{clave}/estatus")
    public ApiResponse<List<CentroTrabajoEstatus>> estatusCentro(@PathVariable String clave) {
        List<CentroTrabajoEstatus> data = service.estatusCentro(clave);
        return ApiResponse.success(data, data.size());
    }

    @GetMapping("/consultas/centros-trabajo/{clave}/plazas")
    public ApiResponse<List<CentroTrabajoPlaza>> plazasCentro(
            @PathVariable String clave,
            @RequestParam(required = false) @Min(0) @Max(1) Integer estatus) {
        List<CentroTrabajoPlaza> data = service.plazasDeCentro(clave, estatus);
        return ApiResponse.success(data, data.size());
    }

    @GetMapping("/consultas/categorias")
    public ApiResponse<List<Categoria>> categorias(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer modelo) {
        List<Categoria> data = service.categorias(q, modelo);
        return ApiResponse.success(data, data.size());
    }

    @GetMapping("/consultas/categorias/{clave}")
    public ApiResponse<Categoria> categoria(@PathVariable String clave) {
        return ApiResponse.success(service.categoria(clave));
    }

    @GetMapping("/consultas/categorias/{clave}/conceptos")
    public ApiResponse<List<CategoriaConcepto>> tabulador(
            @PathVariable String clave,
            @RequestParam(required = false) @Min(1) @Max(999999) Integer qna,
            @RequestParam(required = false) @Min(1) @Max(9) Integer nivelSueldo,
            @RequestParam(defaultValue = "false") boolean historial) {
        List<CategoriaConcepto> data = service.tabulador(clave, qna, nivelSueldo, historial);
        return ApiResponse.success(data, data.size());
    }

    @GetMapping("/consultas/conceptos")
    public ApiResponse<List<Concepto>> conceptos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String percDed) {
        List<Concepto> data = service.conceptos(q, percDed);
        return ApiResponse.success(data, data.size());
    }
}
