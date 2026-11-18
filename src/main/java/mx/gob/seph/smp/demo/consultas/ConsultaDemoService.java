package mx.gob.seph.smp.demo.consultas;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConsultaDemoService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    public record EmpleadoOpcion(int id, String nombreCompleto, String rfc, boolean activo) {
    }

    public record EmpleadoResumen(int id, String rfc, String curp, String nombreCompleto, boolean activo) {
    }

    public record EmpleadoDatos(
            int id, String rfc, String curp, String nombre, String primerApellido, String segundoApellido,
            String sexo, String estadoCivil, String nivelAcademico, String perfil, String nss, String regimen,
            String tipoContratacion, Integer qnaIngreso, Integer qnaGobierno, Integer qnaSep, boolean activo) {
    }

    public record EmpleadoPlaza(
            int plazaId, String cvePlaza, String categoria, String categoriaDescripcion, Integer horas,
            String centroTrabajo, Integer estatusId, String estatus, Integer nivelSueldo) {
    }

    public record HistorialMovimiento(
            int id, Integer acuerdo, Integer plazaId, String cvePlaza, String categoria, Integer empleadoId,
            String rfc, String nombreCompleto, String movimiento, String motivo, Integer qnaIni, Integer qnaFin,
            String centroTrabajo, String status, boolean rechazado) {
    }

    public record PlazaResumen(
            int id, String cvePlaza, String categoria, Integer horas, String centroTrabajo, String estatus) {
    }

    public record PlazaDetalle(
            int id, String cvePlaza, String codPago, String unidad, String subunidad, String categoria,
            String categoriaDescripcion, Integer horas, Integer consPlaza, String centroTrabajo,
            Integer estatusId, String estatus, String codigoCreacion, Integer qnaIni, Integer qnaFin,
            String folio, String fechaFolio) {
    }

    public record PlazaOcupante(
            int empleadoId, String rfc, String nombreCompleto, Integer estatusId, String estatus,
            Integer nivelSueldo) {
    }

    public record CentroTrabajoResumen(
            String clave, String nombre, String nivel, String turno, String localidad, String municipio,
            Integer estatusId, String estatus) {
    }

    public record CentroTrabajoDetalle(
            String clave, Integer catCctId, String nombre, String tipo, String nivel, String subnivel,
            String servicio, String sostenimiento, String dependenciaAdministrativa, String domicilio,
            String numeroExterior, String entreCalle, String yCalle, String colonia, String localidad,
            String municipio, String codigoPostal, String turnoId, String turno, String zonaEscolar,
            String cctZona, String sector, String cctSector, String servicioRegional, String director,
            String lada, String telefono, String correo, String paginaWeb, Integer estatusId,
            String estatus, String fechaAlta, String fechaClausura, String fechaReapertura, String fechaCambio) {
    }

    public record CentroTrabajoEstatus(
            Integer estatusId, String estatus, int plazas, int plazasJornada, Integer horas) {
    }

    public record CentroTrabajoPlaza(
            int id, String cvePlaza, String categoria, String categoriaDescripcion, Integer horas,
            Integer estatusId, String estatus, String ocupantes) {
    }

    public record Categoria(
            String clave, String descripcion, Integer modeloId, String modelo, Integer subsistemaId,
            Integer subsubsistemaId, String niveles, String zonaEconomica, String tipoContratacion,
            String clasificacion, String areaAdscripcion, Integer hrsser, Integer hrscla, Integer hrsdoc,
            Integer hrscom, Integer hsemax, Integer hclmax) {
    }

    public record CategoriaConcepto(
            String percDed, String concepto, String descripcion, String tipoConcepto,
            String tipoConceptoDescripcion, Integer nivelSueldo, Integer qnaIni, Integer qnaFin,
            Double impZe1, Double impZe2, Double impZe3) {
    }

    public record Concepto(
            String percDed, String clave, String descripcion, Integer partida, String tipoConcepto) {
    }

    public record PlazaCompatibilidad(
            int plazaId, String cvePlaza, String codPago, String unidad, String subunidad,
            String categoria, String categoriaDescripcion, Integer horas, Integer horasJornada,
            Integer hsemax, Integer hclmax, String centroTrabajo, Integer estatusId, String estatus) {
    }

    public record UnidadPagadora(
            String unidad, String subunidad, int horasSemanaMes, List<PlazaCompatibilidad> plazas) {
    }

    public record CompatibilidadEmpleo(
            int empleadoId, String rfc, String nombreCompleto, int totalPlazas, int horasSemanaMes,
            int plazasJornada, int horasJornada, List<UnidadPagadora> unidades) {
    }

    private final ConsultaRepository repository;

    public ConsultaDemoService(ConsultaRepository repository) {
        this.repository = repository;
    }

    public List<EmpleadoOpcion> opcionesEmpleados(String query, int requestedLimit, boolean includeInactive) {
        String normalized = normalize(query);
        return repository.employeeOptions().stream()
                .filter(employee -> includeInactive || employee.activo())
                .filter(employee -> normalized.isEmpty() || containsInOrder(
                        employee.nombreCompleto() + " " + Objects.toString(employee.rfc(), "") + " " + employee.id(),
                        normalized))
                .sorted(Comparator.comparing(EmpleadoOpcion::nombreCompleto))
                .limit(limit(requestedLimit))
                .toList();
    }

    public List<EmpleadoResumen> buscarEmpleados(String rfc, int requestedLimit) {
        String normalized = normalize(rfc);
        return repository.employeeSummaries().stream()
                .filter(employee -> normalized.isEmpty() || normalize(employee.rfc()).contains(normalized))
                .limit(limit(requestedLimit))
                .toList();
    }

    public EmpleadoDatos empleado(int id) {
        return employeeDetails(id);
    }

    public List<EmpleadoPlaza> plazasDeEmpleado(int id) {
        employeeDetails(id);
        return repository.employeePositions(id);
    }

    public List<HistorialMovimiento> historialDeEmpleado(int id) {
        employeeDetails(id);
        return repository.employeeHistory(id);
    }

    public CompatibilidadEmpleo compatibilidad(int employeeId) {
        EmpleadoDatos employee = employeeDetails(employeeId);
        List<PlazaCompatibilidad> positions = repository.employeeCompatibility(employeeId);
        Map<String, List<PlazaCompatibilidad>> byUnit = positions.stream()
                .collect(Collectors.groupingBy(
                        position -> Objects.toString(position.unidad(), "") + "|"
                                + Objects.toString(position.subunidad(), ""),
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<UnidadPagadora> units = byUnit.values().stream().map(unitPositions -> {
            PlazaCompatibilidad first = unitPositions.get(0);
            return new UnidadPagadora(first.unidad(), first.subunidad(),
                    sum(unitPositions, position -> position.horas() == null ? 0 : position.horas()), unitPositions);
        }).toList();

        return new CompatibilidadEmpleo(
                employeeId,
                employee.rfc(),
                fullName(employee),
                positions.size(),
                sum(positions, position -> position.horas() == null ? 0 : position.horas()),
                (int) positions.stream().filter(position -> Objects.equals(position.horas(), 0)).count(),
                sum(positions, position -> position.horasJornada() == null ? 0 : position.horasJornada()),
                units);
    }

    public List<PlazaResumen> buscarPlazas(String key, int requestedLimit) {
        String normalized = normalize(key);
        return repository.positionSummaries().stream()
                .filter(position -> normalized.isEmpty() || normalize(position.cvePlaza()).contains(normalized))
                .limit(limit(requestedLimit))
                .toList();
    }

    public PlazaDetalle plaza(int id) {
        return repository.positionDetails(id).orElseThrow(() -> notFound("No se encontró la plaza."));
    }

    public List<PlazaOcupante> ocupantes(int id) {
        plaza(id);
        return repository.positionOccupants(id);
    }

    public List<HistorialMovimiento> historialDePlaza(int id, String cct) {
        plaza(id);
        return repository.positionHistory(id, blankToNull(cct));
    }

    public List<CentroTrabajoResumen> buscarCentros(String query, Integer status, int requestedLimit) {
        String normalized = normalize(query);
        return repository.workCenters().stream()
                .filter(center -> status == null || Objects.equals(status, center.estatusId()))
                .filter(center -> normalized.isEmpty() || containsInOrder(
                        center.clave() + " " + Objects.toString(center.nombre(), "") + " "
                                + Objects.toString(center.localidad(), ""),
                        normalized))
                .map(center -> new CentroTrabajoResumen(
                        center.clave(), center.nombre(), center.nivel(), center.turno(),
                        center.localidad(), center.municipio(), center.estatusId(), center.estatus()))
                .limit(limit(requestedLimit))
                .toList();
    }

    public CentroTrabajoDetalle centroTrabajo(String key) {
        return repository.workCenter(key).orElseThrow(() -> notFound("No se encontró el centro de trabajo."));
    }

    public List<CentroTrabajoEstatus> estatusCentro(String key) {
        centroTrabajo(key);
        Map<Integer, List<CentroTrabajoPlaza>> byStatus = repository.workCenterPositions(key, null).stream()
                .collect(Collectors.groupingBy(CentroTrabajoPlaza::estatusId, LinkedHashMap::new, Collectors.toList()));
        return byStatus.entrySet().stream().map(entry -> {
            List<CentroTrabajoPlaza> positions = entry.getValue();
            return new CentroTrabajoEstatus(
                    entry.getKey(),
                    positions.get(0).estatus(),
                    positions.size(),
                    (int) positions.stream().filter(position -> Objects.equals(position.horas(), 0)).count(),
                    sum(positions, position -> position.horas() == null ? 0 : position.horas()));
        }).toList();
    }

    public List<CentroTrabajoPlaza> plazasDeCentro(String key, Integer status) {
        centroTrabajo(key);
        return repository.workCenterPositions(key, status);
    }

    public List<Categoria> categorias(String query, Integer model) {
        String normalized = normalize(query);
        return repository.categories().stream()
                .filter(category -> model == null || Objects.equals(model, category.modeloId()))
                .filter(category -> normalized.isEmpty()
                        || normalize(category.clave() + " " + Objects.toString(category.descripcion(), ""))
                                .contains(normalized))
                .toList();
    }

    public Categoria categoria(String key) {
        return repository.category(key).orElseThrow(() -> notFound("No se encontró la categoría."));
    }

    public List<CategoriaConcepto> tabulador(
            String key, Integer payPeriod, Integer salaryLevel, boolean history) {
        categoria(key);
        int effectivePayPeriod = payPeriod == null ? 202622 : payPeriod;
        return repository.categoryConcepts(key).stream()
                .filter(row -> history || (row.qnaIni() == null || row.qnaIni() <= effectivePayPeriod)
                        && (row.qnaFin() == null || row.qnaFin() >= effectivePayPeriod))
                .filter(row -> salaryLevel == null || Objects.equals(salaryLevel, row.nivelSueldo()))
                .toList();
    }

    public List<Concepto> conceptos(String query, String percDed) {
        String normalized = normalize(query);
        String type = normalize(percDed);
        return repository.concepts().stream()
                .filter(concept -> type.isEmpty() || normalize(concept.percDed()).equals(type))
                .filter(concept -> normalized.isEmpty()
                        || normalize(concept.clave() + " " + Objects.toString(concept.descripcion(), ""))
                                .contains(normalized))
                .toList();
    }

    private EmpleadoDatos employeeDetails(int id) {
        return repository.employeeDetails(id).orElseThrow(() -> notFound("No se encontró el empleado."));
    }

    private static String fullName(EmpleadoDatos employee) {
        return java.util.stream.Stream.of(employee.nombre(), employee.primerApellido(), employee.segundoApellido())
                .filter(Objects::nonNull).filter(value -> !value.isBlank()).collect(Collectors.joining(" "));
    }

    private static <T> int sum(List<T> values, java.util.function.ToIntFunction<T> mapper) {
        return values.stream().mapToInt(mapper).sum();
    }

    private static int limit(int requested) {
        return requested <= 0 ? DEFAULT_LIMIT : Math.min(requested, MAX_LIMIT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static boolean containsInOrder(String haystack, String words) {
        int position = 0;
        for (String word : words.split("\\s+")) {
            if (word.isBlank()) continue;
            int found = normalize(haystack).indexOf(word, position);
            if (found < 0) return false;
            position = found + word.length();
        }
        return true;
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
