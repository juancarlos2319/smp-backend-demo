package mx.gob.seph.smp.demo.fup.nuevoingreso;

import java.util.List;

/** Contratos de `/fup/movimientos/nuevo-ingreso` (ver `nuevo-ingreso.model.ts` del front). */
public final class NuevoIngresoModels {
    private NuevoIngresoModels() {
    }

    public record CatalogoOpcion(int id, String descripcion) {
    }

    public record ClaveOpcion(String cve, String descripcion) {
    }

    public record Catalogos(
            boolean puedeRegistrar, List<CatalogoOpcion> estadosCiviles, List<CatalogoOpcion> nivelesAcademicos,
            List<CatalogoOpcion> tiposTelefono, List<ClaveOpcion> motivos, List<ClaveOpcion> motivosAdicionales,
            List<CatalogoOpcion> niveles, List<ClaveOpcion> entidades, List<CatalogoOpcion> tiposDomicilio) {
    }

    public record PlazaNuevoIngreso(
            int id, String cvePlaza, String categoria, String categoriaDescripcion, Integer horas, Integer catCctId,
            String centroTrabajo, Integer estatusId, String estatus) {
    }

    public record CentroTrabajoNuevoIngreso(
            Integer catCctId, String clave, String nombre, String nivel, String turno, String municipio,
            Integer estatusId, String estatus) {
    }

    public record Domicilio(
            String calle, String numExt, String numInt, String colonia, String cveEnt, String cveMun, String cveLoc,
            String cp, Integer catTipoDomicilioId) {
    }

    public record EmpleadoNuevoIngreso(
            int id, String rfc, String curp, String primerApellido, String segundoApellido, String nombre,
            Integer catSexoId, Integer catEstadoCivilId, Integer catNivelAcademicoId, Integer qnaini, Boolean activo,
            String telefono, Integer catTipoTelefonoId, String correo, String clabe, String cveEntNacimiento,
            String cpSat, Domicilio domicilio) {
    }

    public record AltaActiva(
            int acuerdo, int tabPlazaId, String plaza, String centroTrabajo, String cveMovimiento, String cveMotivo,
            Integer qnaIni, Integer qnaFin) {
    }

    public record FolioAltaEnTramite(
            int acuerdo, String status, boolean rechazado, int tabPlazaId, String plaza, String centroTrabajo,
            String cveMovimiento, String cveMotivo, Integer qnaIni, Integer qnaFin) {
    }

    public record Busqueda(
            String tipo, boolean puedeCapturar, EmpleadoNuevoIngreso empleado, List<AltaActiva> altasActivas,
            List<FolioAltaEnTramite> foliosEnTramite) {
    }

    public record PlazaRequest(Integer tabPlazaId, Integer catCctId, String cveMotivo, Integer qnaIni, Integer qnaFin) {
    }

    /** Parte `datos` del POST multipart. */
    public record NuevoIngresoRequest(
            String rfc, String curp, String primerApellido, String segundoApellido, String nombre,
            Integer catEstadoCivilId, Integer catNivelAcademicoId, String cveEntNacimiento, String cpSat,
            Domicilio domicilio, String telefono, Integer catTipoTelefonoId, String correo, String clabe,
            Integer catNivelId, List<PlazaRequest> plazas) {
    }

    public record Resultado(int acuerdo, int tabEmpleadoId, String tipo, int movimientos) {
    }
}
