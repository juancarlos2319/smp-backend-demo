package mx.gob.seph.smp.demo.fup.nuevoingreso;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import mx.gob.seph.smp.demo.api.ReglaNegocioException;
import mx.gob.seph.smp.demo.cuenta.CuentaRepository;
import mx.gob.seph.smp.demo.fup.comun.FupRepository;
import mx.gob.seph.smp.demo.fup.comun.Validaciones;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.AltaActiva;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.Busqueda;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.CatalogoOpcion;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.Catalogos;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.CentroTrabajoNuevoIngreso;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.ClaveOpcion;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.Domicilio;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.EmpleadoNuevoIngreso;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.FolioAltaEnTramite;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.NuevoIngresoRequest;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.PlazaNuevoIngreso;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.PlazaRequest;
import mx.gob.seph.smp.demo.fup.nuevoingreso.NuevoIngresoModels.Resultado;
import mx.gob.seph.smp.demo.seguridad.Permiso;
import mx.gob.seph.smp.demo.seguridad.PermisosService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Nuevo ingreso y reingreso (MOV_NUEVO_INGRESO, movimiento 01; plazas adicionales con 02). Todas las plazas van
 * en un folio nuevo en estatus A; la plaza pasa a "En trámite" y el empleado queda inactivo hasta Pagos.
 */
@Service
public class NuevoIngresoService {
    public static final String MODULO = "MOV_NUEVO_INGRESO";
    private static final int MAX_PLAZAS = 20;
    private static final int LIMIT_DEFAULT = 20;
    private static final int LIMIT_MAX = 50;

    private final JdbcClient jdbc;
    private final PermisosService permisos;
    private final CuentaRepository cuentas;
    private final FupRepository fup;

    public NuevoIngresoService(JdbcClient jdbc, PermisosService permisos, CuentaRepository cuentas, FupRepository fup) {
        this.jdbc = jdbc;
        this.permisos = permisos;
        this.cuentas = cuentas;
        this.fup = fup;
    }

    // Catálogos

    public Catalogos catalogos(long userId) {
        List<ClaveOpcion> motivos = motivos(userId, "01");
        List<ClaveOpcion> adicionales = motivos(userId, "02");
        List<CatalogoOpcion> niveles = niveles(userId);
        return new Catalogos(
                puedeRegistrar(userId, motivos, niveles),
                opciones("SELECT id, descripcion FROM cat_estado_civil ORDER BY id"),
                opciones("SELECT id, descripcion FROM cat_nivel_academico ORDER BY descripcion"),
                opciones("SELECT id, descripcion FROM cat_tipo_telefono ORDER BY id"),
                motivos,
                adicionales,
                niveles,
                claves("SELECT cve, descripcion FROM cat_entidades ORDER BY cve"),
                opciones("SELECT id, descripcion FROM cat_tipo_domicilio ORDER BY id"));
    }

    public List<ClaveOpcion> municipios(String cveEnt) {
        return jdbc.sql("SELECT cve_mun AS cve, descripcion FROM cat_municipios WHERE cve_ent = :ent ORDER BY descripcion")
                .param("ent", cveEnt)
                .query((row, n) -> new ClaveOpcion(row.getString("cve"), row.getString("descripcion"))).list();
    }

    public List<ClaveOpcion> localidades(String cveEnt, String cveMun) {
        return jdbc.sql("""
                        SELECT cve_loc AS cve, descripcion FROM cat_localidades
                        WHERE cve_ent = :ent AND cve_mun = :mun ORDER BY descripcion
                        """)
                .param("ent", cveEnt).param("mun", cveMun)
                .query((row, n) -> new ClaveOpcion(row.getString("cve"), row.getString("descripcion"))).list();
    }

    /** Plazas cuya clave contiene el texto (4 caracteres como mínimo). */
    public List<PlazaNuevoIngreso> plazas(String clave, Integer limit) {
        String texto = Validaciones.filtroTexto(clave, 4, Validaciones.TEXTO_PLAZA, "texto de la clave de plaza");
        if (texto == null) {
            throw new ReglaNegocioException("Escribe al menos 4 caracteres de la clave de plaza.");
        }
        return jdbc.sql(PLAZA_SELECT + " WHERE upper(p.code) LIKE '%' || :texto || '%' ORDER BY p.code LIMIT :limit")
                .param("texto", texto).param("limit", limit(limit))
                .query((row, n) -> new PlazaNuevoIngreso(row.getInt("id"), row.getString("code"),
                        row.getString("category_code"), row.getString("cat_desc"), (Integer) row.getObject("hours"),
                        (Integer) row.getObject("cat_cct_id"), row.getString("center_code"),
                        row.getInt("status_id"), row.getString("status_name")))
                .list();
    }

    /** CT cuya clave o nombre empieza con el texto (3 caracteres como mínimo). */
    public List<CentroTrabajoNuevoIngreso> centrosTrabajo(String q, Integer limit) {
        String texto = q == null ? "" : q.trim().toUpperCase();
        if (texto.length() < 3) {
            throw new ReglaNegocioException("Escribe al menos 3 caracteres de la clave o del nombre del centro de trabajo.");
        }
        return jdbc.sql("""
                        SELECT (w.details->>'catCctId')::int AS cat_cct_id, w.code, w.name, w.level_name, w.shift_name,
                               w.municipality, w.status_id, w.status_name
                        FROM demo_work_centers w
                        WHERE upper(w.code) LIKE :texto || '%' OR upper(w.name) LIKE :texto || '%'
                        ORDER BY w.code
                        LIMIT :limit
                        """)
                .param("texto", texto).param("limit", limit(limit))
                .query((row, n) -> new CentroTrabajoNuevoIngreso((Integer) row.getObject("cat_cct_id"),
                        row.getString("code"), row.getString("name"), row.getString("level_name"),
                        row.getString("shift_name"), row.getString("municipality"), row.getInt("status_id"),
                        row.getString("status_name")))
                .list();
    }

    // Búsqueda previa

    public Busqueda buscar(long userId, String rfcParam, String curpParam) {
        String rfc = Validaciones.blankToNull(Validaciones.upperTrim(rfcParam));
        String curp = Validaciones.blankToNull(Validaciones.upperTrim(curpParam));
        if (rfc == null && curp == null) {
            throw new ReglaNegocioException("Escribe el RFC o la CURP.");
        }
        if (rfc != null) {
            Validaciones.rfc(rfc);
        }
        if (curp != null) {
            Validaciones.curp(curp);
        }
        Optional<Integer> empleadoId = empleadoPor(rfc, curp);
        if (empleadoId.isEmpty()) {
            return new Busqueda("NUEVO_INGRESO", true, null, List.of(), List.of());
        }
        List<AltaActiva> altas = altasActivas(empleadoId.get());
        List<FolioAltaEnTramite> enTramite = foliosEnTramite(empleadoId.get());
        return new Busqueda("REINGRESO", altas.isEmpty() && enTramite.isEmpty(), empleado(empleadoId.get()), altas,
                enTramite);
    }

    // Registro

    @Transactional
    public Resultado registrar(long userId, NuevoIngresoRequest req, MultipartFile clabePdf) {
        List<ClaveOpcion> motivos01 = motivos(userId, "01");
        List<ClaveOpcion> motivos02 = motivos(userId, "02");
        List<CatalogoOpcion> niveles = niveles(userId);
        if (!puedeRegistrar(userId, motivos01, niveles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No puedes registrar nuevos ingresos: revisa tu permiso AGREGAR, la etapa de registro y tu nivel.");
        }
        if (req == null) {
            throw new ReglaNegocioException("Faltan los datos del registro.");
        }

        // Persona
        String rfc = Validaciones.upperTrim(req.rfc());
        String curp = Validaciones.upperTrim(req.curp());
        Validaciones.rfc(rfc);
        Validaciones.curp(curp);
        Validaciones.curpCoincideConRfc(curp, rfc);
        String nombre = obligatorio(req.nombre(), "El nombre");
        String primerApellido = obligatorio(req.primerApellido(), "El primer apellido");
        String segundoApellido = Validaciones.blankToNull(Validaciones.upperTrim(req.segundoApellido()));
        existe("cat_estado_civil", req.catEstadoCivilId(), "El estado civil");
        existe("cat_nivel_academico", req.catNivelAcademicoId(), "El nivel académico");
        String entNacimiento = Validaciones.blankToNull(req.cveEntNacimiento());
        if (entNacimiento != null && count("SELECT count(*) FROM cat_entidades WHERE cve = :v", entNacimiento) == 0) {
            throw new ReglaNegocioException("No existe la entidad de nacimiento " + entNacimiento + ".");
        }
        String cpSat = Validaciones.blankToNull(req.cpSat());
        if (cpSat != null && !Validaciones.CP.matcher(cpSat).matches()) {
            throw new ReglaNegocioException("El código postal fiscal debe tener 5 dígitos.");
        }

        // Contacto y CLABE
        String telefono = req.telefono() == null ? "" : req.telefono().replaceAll("[\\s-]", "");
        if (!telefono.matches("\\d{10}")) {
            throw new ReglaNegocioException("El teléfono debe tener 10 dígitos.");
        }
        existe("cat_tipo_telefono", req.catTipoTelefonoId(), "El tipo de teléfono");
        String correo = Validaciones.blankToNull(req.correo());
        if (correo != null && !Validaciones.CORREO.matcher(correo).matches()) {
            throw new ReglaNegocioException("El correo no es válido.");
        }
        String clabe = req.clabe() == null ? "" : req.clabe().trim();
        if (!Validaciones.clabeValida(clabe)) {
            throw new ReglaNegocioException("La CLABE debe tener 18 dígitos y un dígito verificador correcto.");
        }

        Domicilio domicilio = validarDomicilio(req.domicilio());

        // Folio y plazas
        if (req.catNivelId() == null || niveles.stream().noneMatch(n -> n.id() == req.catNivelId())) {
            throw new ReglaNegocioException("No puedes capturar folios en el nivel " + req.catNivelId() + ".");
        }
        List<PlazaValidada> plazas = validarPlazas(req.plazas(), motivos01, motivos02);

        // Empleado: si el RFC existe es reingreso
        Optional<Integer> porRfc = empleadoPor(rfc, null);
        Optional<Integer> porCurp = empleadoPor(null, curp);
        boolean reingreso = porRfc.isPresent();
        if (reingreso && porCurp.isPresent() && !porCurp.get().equals(porRfc.get())) {
            throw new ReglaNegocioException("La CURP pertenece a otro empleado.");
        }
        if (!reingreso && porCurp.isPresent()) {
            throw new ReglaNegocioException("La CURP ya pertenece a un empleado con otro RFC.");
        }
        if (reingreso && (!altasActivas(porRfc.get()).isEmpty() || !foliosEnTramite(porRfc.get()).isEmpty())) {
            throw new ReglaNegocioException(
                    "El empleado tiene altas activas o folios en trámite: no se puede capturar su reingreso.");
        }

        int acuerdo = siguienteAcuerdo(req.catNivelId());
        int qnaIngreso = plazas.get(0).request().qnaIni();
        int empleadoId = reingreso ? porRfc.get() : jdbc.sql("SELECT nextval('demo_employee_seq')").query(Integer.class).single();
        String nombreCompleto = String.join(" ",
                java.util.stream.Stream.of(nombre, primerApellido, segundoApellido).filter(s -> s != null).toList());
        guardarEmpleado(empleadoId, reingreso, rfc, curp, nombre, primerApellido, segundoApellido, nombreCompleto,
                req, telefono, correo, clabe, entNacimiento, cpSat, qnaIngreso);
        guardarDomicilio(empleadoId, domicilio);

        jdbc.sql("""
                        INSERT INTO fup_folios (acuerdo, status, rechazado, ts_captura, cat_nivel_id, empleado_id,
                                                crea_empleado, capturo_user_id)
                        VALUES (:acuerdo, 'A', FALSE, now(), :nivel, :empleado, :crea, :user)
                        """)
                .param("acuerdo", acuerdo).param("nivel", req.catNivelId()).param("empleado", empleadoId)
                .param("crea", !reingreso).param("user", userId).update();

        int orden = 1;
        for (PlazaValidada plaza : plazas) {
            jdbc.sql("""
                            INSERT INTO fup_movimientos (acuerdo, orden, tab_plaza_id, cct_clave, cve_movimiento,
                                                         cve_motivo, qna_ini, qna_fin, con_foto, prev_status_id,
                                                         prev_status_name, prev_center_code)
                            VALUES (:acuerdo, :orden, :plaza, :cct, :mov, :motivo, :ini, :fin, TRUE, :prevStatus,
                                    :prevName, :prevCenter)
                            """)
                    .param("acuerdo", acuerdo).param("orden", orden)
                    .param("plaza", plaza.request().tabPlazaId()).param("cct", plaza.cctClave())
                    .param("mov", orden == 1 ? "01" : "02").param("motivo", plaza.request().cveMotivo())
                    .param("ini", plaza.request().qnaIni()).param("fin", plaza.request().qnaFin())
                    .param("prevStatus", plaza.statusId()).param("prevName", plaza.statusName())
                    .param("prevCenter", plaza.centerCode())
                    .update();
            fup.actualizarPlaza(plaza.request().tabPlazaId(), FupRepository.PLAZA_EN_TRAMITE, "En trámite",
                    plaza.cctClave());
            orden++;
        }

        if (clabePdf != null && !clabePdf.isEmpty()) {
            guardarPdf(acuerdo, clabePdf);
        }
        return new Resultado(acuerdo, empleadoId, reingreso ? "REINGRESO" : "NUEVO_INGRESO", plazas.size());
    }

    // Reglas

    private boolean puedeRegistrar(long userId, List<ClaveOpcion> motivos01, List<CatalogoOpcion> niveles) {
        return permisos.tienePermiso(userId, MODULO, Permiso.AGREGAR)
                && permisos.etapas(userId).contains("registro")
                && !motivos01.isEmpty()
                && !niveles.isEmpty();
    }

    /** Motivos del movimiento que el usuario cubre con AGREGAR. */
    private List<ClaveOpcion> motivos(long userId, String movimiento) {
        if (!permisos.tienePermiso(userId, MODULO, Permiso.AGREGAR)) {
            return List.of();
        }
        return jdbc.sql("SELECT cve_motivo, descripcion FROM cat_motivos WHERE cve_movimiento = :mov ORDER BY cve_motivo")
                .param("mov", movimiento)
                .query((row, n) -> new ClaveOpcion(row.getString("cve_motivo"), row.getString("descripcion")))
                .list().stream()
                .filter(m -> permisos.cubre(userId, Permiso.AGREGAR, movimiento, m.cve()))
                .toList();
    }

    /** Niveles con rango de folios; si el usuario tiene nivel asignado (`area`), solo ese. */
    private List<CatalogoOpcion> niveles(long userId) {
        Integer area = cuentas.porId(userId).map(CuentaRepository.Cuenta::area).orElse(null);
        String sql = "SELECT n.id, n.nivel FROM cat_nivel_rh n JOIN fup_control c ON c.cat_nivel_id = n.id"
                + (area == null ? "" : " WHERE n.id = :area") + " ORDER BY n.id";
        var spec = jdbc.sql(sql);
        if (area != null) {
            spec = spec.param("area", area);
        }
        return spec.query((row, n) -> new CatalogoOpcion(row.getInt("id"), row.getString("nivel"))).list();
    }

    private Domicilio validarDomicilio(Domicilio d) {
        if (d == null) {
            throw new ReglaNegocioException("El domicilio es obligatorio.");
        }
        String calle = obligatorio(d.calle(), "La calle del domicilio");
        if (d.cveEnt() == null || d.cveMun() == null) {
            throw new ReglaNegocioException("La entidad y el municipio del domicilio son obligatorios.");
        }
        if (jdbc.sql("SELECT count(*) FROM cat_municipios WHERE cve_ent = :ent AND cve_mun = :mun")
                .param("ent", d.cveEnt()).param("mun", d.cveMun()).query(Integer.class).single() == 0) {
            throw new ReglaNegocioException("El municipio " + d.cveMun() + " no es de la entidad " + d.cveEnt() + ".");
        }
        String loc = Validaciones.blankToNull(d.cveLoc());
        if (loc != null && jdbc.sql("""
                        SELECT count(*) FROM cat_localidades WHERE cve_ent = :ent AND cve_mun = :mun AND cve_loc = :loc
                        """)
                .param("ent", d.cveEnt()).param("mun", d.cveMun()).param("loc", loc)
                .query(Integer.class).single() == 0) {
            throw new ReglaNegocioException("La localidad " + loc + " no es del municipio " + d.cveMun() + ".");
        }
        if (d.cp() == null || !Validaciones.CP.matcher(d.cp().trim()).matches()) {
            throw new ReglaNegocioException("El código postal del domicilio debe tener 5 dígitos.");
        }
        if (d.catTipoDomicilioId() != null) {
            existe("cat_tipo_domicilio", d.catTipoDomicilioId(), "El tipo de domicilio");
        }
        return new Domicilio(calle, upperOrNull(d.numExt()), upperOrNull(d.numInt()), upperOrNull(d.colonia()),
                d.cveEnt(), d.cveMun(), loc, d.cp().trim(), d.catTipoDomicilioId());
    }

    private record PlazaValidada(
            PlazaRequest request, String cctClave, int statusId, String statusName, String centerCode) {
    }

    private List<PlazaValidada> validarPlazas(
            List<PlazaRequest> plazas, List<ClaveOpcion> motivos01, List<ClaveOpcion> motivos02) {
        if (plazas == null || plazas.isEmpty() || plazas.size() > MAX_PLAZAS) {
            throw new ReglaNegocioException("El folio lleva de 1 a " + MAX_PLAZAS + " plazas.");
        }
        if (plazas.size() > 1 && motivos02.isEmpty()) {
            throw new ReglaNegocioException("Tu rol no puede dar de alta plazas adicionales (movimiento 02).");
        }
        Set<Integer> vistas = new HashSet<>();
        List<PlazaValidada> result = new ArrayList<>();
        for (int i = 0; i < plazas.size(); i++) {
            PlazaRequest p = plazas.get(i);
            String fila = "Plaza " + (i + 1) + ": ";
            if (p.tabPlazaId() == null || !vistas.add(p.tabPlazaId())) {
                throw new ReglaNegocioException(fila + "falta la plaza o está repetida.");
            }
            List<ClaveOpcion> permitidos = i == 0 ? motivos01 : motivos02;
            if (p.cveMotivo() == null || permitidos.stream().noneMatch(m -> m.cve().equals(p.cveMotivo()))) {
                throw new ReglaNegocioException(fila + "el motivo " + p.cveMotivo() + " no es válido para el movimiento "
                        + (i == 0 ? "01" : "02") + ".");
            }
            Validaciones.quincena(p.qnaIni(), "quincena de inicio");
            if (p.qnaFin() == null || p.qnaFin() != FupRepository.QNA_SIN_TERMINO) {
                Validaciones.quincena(p.qnaFin(), "quincena de fin");
                if (p.qnaFin() < p.qnaIni()) {
                    throw new ReglaNegocioException(fila + "la quincena de fin es anterior a la de inicio.");
                }
            }
            var plaza = jdbc.sql("SELECT code, status_id, status_name, center_code FROM demo_positions WHERE id = :id")
                    .param("id", p.tabPlazaId())
                    .query((row, n) -> new Object[] {row.getString("code"), row.getInt("status_id"),
                            row.getString("status_name"), row.getString("center_code")})
                    .optional()
                    .orElseThrow(() -> new ReglaNegocioException(fila + "no existe la plaza " + p.tabPlazaId() + "."));
            if ((int) plaza[1] != FupRepository.PLAZA_VACANTE) {
                throw new ReglaNegocioException(fila + "la plaza " + plaza[0] + " no está vacante (" + plaza[2] + ").");
            }
            String cct = jdbc.sql("SELECT code FROM demo_work_centers WHERE (details->>'catCctId')::int = :id")
                    .param("id", p.catCctId() == null ? -1 : p.catCctId())
                    .query(String.class).optional()
                    .orElseThrow(() -> new ReglaNegocioException(fila + "no existe el centro de trabajo."));
            result.add(new PlazaValidada(p, cct, (int) plaza[1], (String) plaza[2], (String) plaza[3]));
        }
        return result;
    }

    private int siguienteAcuerdo(int nivelId) {
        return jdbc.sql("""
                        UPDATE fup_control SET ultimo = ultimo + 1
                        WHERE cat_nivel_id = :nivel AND ultimo < folio_fin
                        RETURNING ultimo
                        """)
                .param("nivel", nivelId).query(Integer.class).optional()
                .orElseThrow(() -> new ReglaNegocioException("Se agotaron los folios del nivel " + nivelId + "."));
    }

    // Empleado

    private Optional<Integer> empleadoPor(String rfc, String curp) {
        if (rfc != null) {
            Optional<Integer> id = jdbc.sql("SELECT id FROM demo_employees WHERE upper(rfc) = :rfc")
                    .param("rfc", rfc).query(Integer.class).optional();
            if (id.isPresent() || curp == null) {
                return id;
            }
        }
        if (curp == null) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT id FROM demo_employees WHERE upper(curp) = :curp")
                .param("curp", curp).query(Integer.class).optional();
    }

    private EmpleadoNuevoIngreso empleado(int id) {
        return jdbc.sql("""
                        SELECT e.id, e.rfc, e.curp, e.active,
                               COALESCE(fe.primer_apellido, e.details->>'primerApellido') AS primer_apellido,
                               COALESCE(fe.segundo_apellido, e.details->>'segundoApellido') AS segundo_apellido,
                               COALESCE(fe.nombre, e.details->>'nombre') AS nombre,
                               fe.cat_estado_civil_id, fe.cat_nivel_academico_id, fe.qnaini, fe.telefono,
                               fe.cat_tipo_telefono_id, fe.correo, fe.clabe, fe.cve_ent_nacimiento, fe.cp_sat,
                               d.employee_id AS dom, d.calle, d.num_ext, d.num_int, d.colonia, d.cve_ent, d.cve_mun,
                               d.cve_loc, d.cp, d.cat_tipo_domicilio_id
                        FROM demo_employees e
                        LEFT JOIN fup_empleados fe ON fe.employee_id = e.id
                        LEFT JOIN fup_domicilios d ON d.employee_id = e.id
                        WHERE e.id = :id
                        """)
                .param("id", id)
                .query((row, n) -> {
                    String curp = row.getString("curp");
                    Domicilio domicilio = row.getObject("dom") == null ? null : new Domicilio(
                            row.getString("calle"), row.getString("num_ext"), row.getString("num_int"),
                            row.getString("colonia"), row.getString("cve_ent"), row.getString("cve_mun"),
                            row.getString("cve_loc"), row.getString("cp"),
                            (Integer) row.getObject("cat_tipo_domicilio_id"));
                    return new EmpleadoNuevoIngreso(row.getInt("id"), row.getString("rfc"), curp,
                            row.getString("primer_apellido"), row.getString("segundo_apellido"),
                            row.getString("nombre"), sexoId(curp), (Integer) row.getObject("cat_estado_civil_id"),
                            (Integer) row.getObject("cat_nivel_academico_id"), (Integer) row.getObject("qnaini"),
                            row.getBoolean("active"), row.getString("telefono"),
                            (Integer) row.getObject("cat_tipo_telefono_id"), row.getString("correo"),
                            row.getString("clabe"), row.getString("cve_ent_nacimiento"), row.getString("cp_sat"),
                            domicilio);
                })
                .single();
    }

    /** Altas (01/02) procesadas y vigentes: las plazas que ocupa el empleado. */
    private List<AltaActiva> altasActivas(int empleadoId) {
        return jdbc.sql("""
                        SELECT o.position_id, p.code, p.center_code,
                               f.acuerdo AS f_acuerdo, f.cve_movimiento, f.cve_motivo, f.qna_ini, f.qna_fin,
                               (h.data->>'acuerdo')::int AS h_acuerdo, (h.data->>'qnaIni')::int AS h_qna_ini,
                               (h.data->>'qnaFin')::int AS h_qna_fin
                        FROM demo_position_occupants o
                        JOIN demo_positions p ON p.id = o.position_id
                        LEFT JOIN LATERAL (
                            SELECT ff.acuerdo, fm.cve_movimiento, fm.cve_motivo, fm.qna_ini, fm.qna_fin
                            FROM fup_movimientos fm
                            JOIN fup_folios ff ON ff.acuerdo = fm.acuerdo
                            WHERE ff.status = 'P' AND NOT ff.deleted AND ff.empleado_id = o.employee_id
                              AND fm.tab_plaza_id = o.position_id
                            ORDER BY ff.acuerdo DESC LIMIT 1
                        ) f ON TRUE
                        LEFT JOIN LATERAL (
                            SELECT data FROM demo_movement_history h
                            WHERE h.employee_id = o.employee_id AND h.position_id = o.position_id
                            ORDER BY h.id DESC LIMIT 1
                        ) h ON TRUE
                        WHERE o.employee_id = :id
                        ORDER BY o.position_id
                        """)
                .param("id", empleadoId)
                .query((row, n) -> {
                    boolean deFup = row.getObject("f_acuerdo") != null;
                    Integer acuerdo = deFup ? (Integer) row.getObject("f_acuerdo") : (Integer) row.getObject("h_acuerdo");
                    Integer qnaFin = deFup ? (Integer) row.getObject("qna_fin") : (Integer) row.getObject("h_qna_fin");
                    return new AltaActiva(acuerdo == null ? 0 : acuerdo, row.getInt("position_id"),
                            row.getString("code"), row.getString("center_code"),
                            deFup ? row.getString("cve_movimiento") : "01",
                            deFup ? row.getString("cve_motivo") : null,
                            deFup ? (Integer) row.getObject("qna_ini") : (Integer) row.getObject("h_qna_ini"),
                            qnaFin == null ? FupRepository.QNA_SIN_TERMINO : qnaFin);
                })
                .list();
    }

    private List<FolioAltaEnTramite> foliosEnTramite(int empleadoId) {
        return jdbc.sql("""
                        SELECT ff.acuerdo, ff.status, ff.rechazado, fm.tab_plaza_id, p.code, fm.cct_clave,
                               fm.cve_movimiento, fm.cve_motivo, fm.qna_ini, fm.qna_fin
                        FROM fup_folios ff
                        JOIN fup_movimientos fm ON fm.acuerdo = ff.acuerdo
                        JOIN demo_positions p ON p.id = fm.tab_plaza_id
                        WHERE ff.empleado_id = :id AND ff.status IN ('A', 'F') AND NOT ff.deleted
                          AND fm.cve_movimiento IN ('01', '02')
                        ORDER BY ff.acuerdo, fm.orden
                        """)
                .param("id", empleadoId)
                .query((row, n) -> new FolioAltaEnTramite(row.getInt("acuerdo"), row.getString("status"),
                        row.getBoolean("rechazado"), row.getInt("tab_plaza_id"), row.getString("code"),
                        row.getString("cct_clave"), row.getString("cve_movimiento"), row.getString("cve_motivo"),
                        row.getInt("qna_ini"), row.getInt("qna_fin")))
                .list();
    }

    private void guardarEmpleado(
            int id, boolean reingreso, String rfc, String curp, String nombre, String primerApellido,
            String segundoApellido, String nombreCompleto, NuevoIngresoRequest req, String telefono, String correo,
            String clabe, String entNacimiento, String cpSat, int qnaIngreso) {
        String sexo = switch (curp.charAt(10)) {
            case 'H' -> "M";
            case 'M' -> "F";
            default -> "X";
        };
        String estadoCivil = descripcion("cat_estado_civil", req.catEstadoCivilId());
        String nivelAcademico = descripcion("cat_nivel_academico", req.catNivelAcademicoId());
        String datos = """
                jsonb_build_object('id', CAST(:id AS integer), 'rfc', CAST(:rfc AS text), 'curp', CAST(:curp AS text),
                    'nombre', CAST(:nombre AS text), 'primerApellido', CAST(:ap1 AS text),
                    'segundoApellido', CAST(:ap2 AS text), 'sexo', CAST(:sexo AS text),
                    'estadoCivil', CAST(:estadoCivil AS text), 'nivelAcademico', CAST(:nivelAcademico AS text),
                    'activo', FALSE)""";
        var spec = reingreso
                ? jdbc.sql("UPDATE demo_employees SET curp = :curp, full_name = :fullName, active = FALSE, details = details || "
                        + datos + " WHERE id = :id")
                : jdbc.sql("""
                        INSERT INTO demo_employees (id, rfc, curp, full_name, active, details)
                        VALUES (:id, :rfc, :curp, :fullName, FALSE, %s || jsonb_build_object('perfil', NULL, 'nss', NULL,
                            'regimen', NULL, 'tipoContratacion', NULL, 'qnaIngreso', CAST(:qna AS integer),
                            'qnaGobierno', CAST(:qna AS integer), 'qnaSep', CAST(:qna AS integer)))
                        """.formatted(datos)).param("qna", qnaIngreso);
        spec.param("id", id).param("rfc", rfc).param("curp", curp).param("fullName", nombreCompleto)
                .param("nombre", nombre).param("ap1", primerApellido).param("ap2", segundoApellido)
                .param("sexo", sexo).param("estadoCivil", estadoCivil).param("nivelAcademico", nivelAcademico)
                .update();

        jdbc.sql("DELETE FROM fup_empleados WHERE employee_id = :id").param("id", id).update();
        jdbc.sql("""
                        INSERT INTO fup_empleados (employee_id, primer_apellido, segundo_apellido, nombre,
                                                   cat_estado_civil_id, cat_nivel_academico_id, telefono,
                                                   cat_tipo_telefono_id, correo, clabe, cve_ent_nacimiento, cp_sat, qnaini)
                        VALUES (:id, :ap1, :ap2, :nombre, :ec, :na, :tel, :tt, :correo, :clabe, :ent, :cp, :qna)
                        """)
                .param("id", id).param("ap1", primerApellido).param("ap2", segundoApellido).param("nombre", nombre)
                .param("ec", req.catEstadoCivilId()).param("na", req.catNivelAcademicoId()).param("tel", telefono)
                .param("tt", req.catTipoTelefonoId()).param("correo", correo).param("clabe", clabe)
                .param("ent", entNacimiento).param("cp", cpSat).param("qna", qnaIngreso)
                .update();
    }

    /** Uno por empleado: en un reingreso reemplaza al que tenía. */
    private void guardarDomicilio(int id, Domicilio d) {
        jdbc.sql("DELETE FROM fup_domicilios WHERE employee_id = :id").param("id", id).update();
        jdbc.sql("""
                        INSERT INTO fup_domicilios (employee_id, calle, num_ext, num_int, colonia, cve_ent, cve_mun,
                                                    cve_loc, cp, cat_tipo_domicilio_id)
                        VALUES (:id, :calle, :ext, :int, :colonia, :ent, :mun, :loc, :cp, :tipo)
                        """)
                .param("id", id).param("calle", d.calle()).param("ext", d.numExt()).param("int", d.numInt())
                .param("colonia", d.colonia()).param("ent", d.cveEnt()).param("mun", d.cveMun())
                .param("loc", d.cveLoc()).param("cp", d.cp()).param("tipo", d.catTipoDomicilioId())
                .update();
    }

    private void guardarPdf(int acuerdo, MultipartFile file) {
        String tipo = file.getContentType();
        String nombre = file.getOriginalFilename();
        if (!"application/pdf".equalsIgnoreCase(tipo) && (nombre == null || !nombre.toLowerCase().endsWith(".pdf"))) {
            throw new ReglaNegocioException("El documento de la CLABE debe ser un PDF.");
        }
        try {
            jdbc.sql("INSERT INTO fup_clabe_pdf (acuerdo, nombre, contenido) VALUES (:acuerdo, :nombre, :contenido)")
                    .param("acuerdo", acuerdo).param("nombre", nombre).param("contenido", file.getBytes()).update();
        } catch (IOException exception) {
            throw new ReglaNegocioException("No se pudo leer el PDF de la CLABE.");
        }
    }

    // Utilerías

    private static Integer sexoId(String curp) {
        if (curp == null || curp.length() < 11) {
            return null;
        }
        return switch (curp.charAt(10)) {
            case 'H' -> 1;
            case 'M' -> 2;
            case 'X' -> 3;
            default -> null;
        };
    }

    private String obligatorio(String value, String campo) {
        String limpio = Validaciones.blankToNull(Validaciones.upperTrim(value));
        if (limpio == null) {
            throw new ReglaNegocioException(campo + " es obligatorio.");
        }
        return limpio;
    }

    private static String upperOrNull(String value) {
        return Validaciones.blankToNull(Validaciones.upperTrim(value));
    }

    /** `tabla` es una constante del código, nunca viene del usuario. */
    private void existe(String tabla, Integer id, String campo) {
        if (id == null || count("SELECT count(*) FROM " + tabla + " WHERE id = :v", id) == 0) {
            throw new ReglaNegocioException(campo + " no es válido.");
        }
    }

    private String descripcion(String tabla, Integer id) {
        return jdbc.sql("SELECT descripcion FROM " + tabla + " WHERE id = :id").param("id", id)
                .query(String.class).optional().orElse(null);
    }

    private int count(String sql, Object value) {
        return jdbc.sql(sql).param("v", value).query(Integer.class).single();
    }

    private List<CatalogoOpcion> opciones(String sql) {
        return jdbc.sql(sql).query((row, n) -> new CatalogoOpcion(row.getInt("id"), row.getString("descripcion"))).list();
    }

    private List<ClaveOpcion> claves(String sql) {
        return jdbc.sql(sql).query((row, n) -> new ClaveOpcion(row.getString("cve"), row.getString("descripcion"))).list();
    }

    private static int limit(Integer limit) {
        return limit == null || limit < 1 ? LIMIT_DEFAULT : Math.min(limit, LIMIT_MAX);
    }

    private static final String PLAZA_SELECT = """
            SELECT p.id, p.code, p.category_code, c.description AS cat_desc, p.hours,
                   (w.details->>'catCctId')::int AS cat_cct_id, p.center_code, p.status_id, p.status_name
            FROM demo_positions p
            JOIN demo_categories c ON c.code = p.category_code
            JOIN demo_work_centers w ON w.code = p.center_code""";
}
