-- Movimientos FUP: nuevo ingreso, baja y autorización. Datos sintéticos.
-- Estatus de plaza: 1 Activa (ocupada), 2 Vacante, 3 En trámite (capturada en un folio sin procesar).

-- Catálogos del formulario

CREATE TABLE cat_estado_civil (id INTEGER PRIMARY KEY, descripcion VARCHAR(60) NOT NULL);
INSERT INTO cat_estado_civil VALUES
(1, 'SOLTERO(A)'), (2, 'CASADO(A)'), (3, 'DIVORCIADO(A)'), (4, 'VIUDO(A)'), (5, 'UNIÓN LIBRE');

CREATE TABLE cat_nivel_academico (id INTEGER PRIMARY KEY, descripcion VARCHAR(120) NOT NULL);
INSERT INTO cat_nivel_academico VALUES
(1, 'PRIMARIA'), (2, 'SECUNDARIA'), (3, 'BACHILLERATO'), (4, 'CARRERA TÉCNICA'),
(5, 'NORMAL BÁSICA'), (6, 'NORMAL SUPERIOR'), (7, 'LICENCIATURA EN EDUCACIÓN PRIMARIA'),
(8, 'LICENCIATURA EN EDUCACIÓN PREESCOLAR'), (9, 'LICENCIATURA EN EDUCACIÓN SECUNDARIA'),
(10, 'LICENCIATURA EN PEDAGOGÍA'), (11, 'LICENCIATURA EN CONTADURÍA'), (12, 'LICENCIATURA EN DERECHO'),
(13, 'INGENIERÍA EN SISTEMAS'), (14, 'MAESTRÍA EN EDUCACIÓN'), (15, 'MAESTRÍA EN ADMINISTRACIÓN'),
(16, 'DOCTORADO EN EDUCACIÓN'), (17, 'PASANTE DE LICENCIATURA');

CREATE TABLE cat_tipo_telefono (id INTEGER PRIMARY KEY, descripcion VARCHAR(40) NOT NULL);
INSERT INTO cat_tipo_telefono VALUES (1, 'CELULAR'), (2, 'CASA'), (3, 'OFICINA');

CREATE TABLE cat_tipo_domicilio (id INTEGER PRIMARY KEY, descripcion VARCHAR(40) NOT NULL);
INSERT INTO cat_tipo_domicilio VALUES (1, 'PARTICULAR'), (2, 'FISCAL');

CREATE TABLE cat_entidades (cve CHAR(2) PRIMARY KEY, descripcion VARCHAR(60) NOT NULL);
INSERT INTO cat_entidades VALUES
('01', 'AGUASCALIENTES'), ('02', 'BAJA CALIFORNIA'), ('03', 'BAJA CALIFORNIA SUR'), ('04', 'CAMPECHE'),
('05', 'COAHUILA DE ZARAGOZA'), ('06', 'COLIMA'), ('07', 'CHIAPAS'), ('08', 'CHIHUAHUA'),
('09', 'CIUDAD DE MÉXICO'), ('10', 'DURANGO'), ('11', 'GUANAJUATO'), ('12', 'GUERRERO'),
('13', 'HIDALGO'), ('14', 'JALISCO'), ('15', 'MÉXICO'), ('16', 'MICHOACÁN DE OCAMPO'),
('17', 'MORELOS'), ('18', 'NAYARIT'), ('19', 'NUEVO LEÓN'), ('20', 'OAXACA'),
('21', 'PUEBLA'), ('22', 'QUERÉTARO'), ('23', 'QUINTANA ROO'), ('24', 'SAN LUIS POTOSÍ'),
('25', 'SINALOA'), ('26', 'SONORA'), ('27', 'TABASCO'), ('28', 'TAMAULIPAS'),
('29', 'TLAXCALA'), ('30', 'VERACRUZ DE IGNACIO DE LA LLAVE'), ('31', 'YUCATÁN'), ('32', 'ZACATECAS');

CREATE TABLE cat_municipios (
    cve_ent CHAR(2) NOT NULL REFERENCES cat_entidades(cve),
    cve_mun CHAR(3) NOT NULL,
    descripcion VARCHAR(80) NOT NULL,
    PRIMARY KEY (cve_ent, cve_mun)
);
-- Hidalgo completo; de otras entidades solo algunos
INSERT INTO cat_municipios VALUES
('13', '001', 'ACATLÁN'), ('13', '002', 'ACAXOCHITLÁN'), ('13', '003', 'ACTOPAN'),
('13', '004', 'AGUA BLANCA DE ITURBIDE'), ('13', '005', 'AJACUBA'), ('13', '006', 'ALFAJAYUCAN'),
('13', '007', 'ALMOLOYA'), ('13', '008', 'APAN'), ('13', '009', 'EL ARENAL'), ('13', '010', 'ATITALAQUIA'),
('13', '011', 'ATLAPEXCO'), ('13', '012', 'ATOTONILCO EL GRANDE'), ('13', '013', 'ATOTONILCO DE TULA'),
('13', '014', 'CALNALI'), ('13', '015', 'CARDONAL'), ('13', '016', 'CUAUTEPEC DE HINOJOSA'),
('13', '017', 'CHAPANTONGO'), ('13', '018', 'CHAPULHUACÁN'), ('13', '019', 'CHILCUAUTLA'),
('13', '020', 'ELOXOCHITLÁN'), ('13', '021', 'EMILIANO ZAPATA'), ('13', '022', 'EPAZOYUCAN'),
('13', '023', 'FRANCISCO I. MADERO'), ('13', '024', 'HUASCA DE OCAMPO'), ('13', '025', 'HUAUTLA'),
('13', '026', 'HUAZALINGO'), ('13', '027', 'HUEHUETLA'), ('13', '028', 'HUEJUTLA DE REYES'),
('13', '029', 'HUICHAPAN'), ('13', '030', 'IXMIQUILPAN'), ('13', '031', 'JACALA DE LEDEZMA'),
('13', '032', 'JALTOCÁN'), ('13', '033', 'JUÁREZ HIDALGO'), ('13', '034', 'LOLOTLA'), ('13', '035', 'METEPEC'),
('13', '036', 'SAN AGUSTÍN METZQUITITLÁN'), ('13', '037', 'METZTITLÁN'), ('13', '038', 'MINERAL DEL CHICO'),
('13', '039', 'MINERAL DEL MONTE'), ('13', '040', 'LA MISIÓN'), ('13', '041', 'MIXQUIAHUALA DE JUÁREZ'),
('13', '042', 'MOLANGO DE ESCAMILLA'), ('13', '043', 'NICOLÁS FLORES'), ('13', '044', 'NOPALA DE VILLAGRÁN'),
('13', '045', 'OMITLÁN DE JUÁREZ'), ('13', '046', 'SAN FELIPE ORIZATLÁN'), ('13', '047', 'PACULA'),
('13', '048', 'PACHUCA DE SOTO'), ('13', '049', 'PISAFLORES'), ('13', '050', 'PROGRESO DE OBREGÓN'),
('13', '051', 'MINERAL DE LA REFORMA'), ('13', '052', 'SAN AGUSTÍN TLAXIACA'),
('13', '053', 'SAN BARTOLO TUTOTEPEC'), ('13', '054', 'SAN SALVADOR'), ('13', '055', 'SANTIAGO DE ANAYA'),
('13', '056', 'SANTIAGO TULANTEPEC DE LUGO GUERRERO'), ('13', '057', 'SINGUILUCAN'), ('13', '058', 'TASQUILLO'),
('13', '059', 'TECOZAUTLA'), ('13', '060', 'TENANGO DE DORIA'), ('13', '061', 'TEPEAPULCO'),
('13', '062', 'TEPEHUACÁN DE GUERRERO'), ('13', '063', 'TEPEJI DEL RÍO DE OCAMPO'), ('13', '064', 'TEPETITLÁN'),
('13', '065', 'TETEPANGO'), ('13', '066', 'VILLA DE TEZONTEPEC'), ('13', '067', 'TEZONTEPEC DE ALDAMA'),
('13', '068', 'TIANGUISTENGO'), ('13', '069', 'TIZAYUCA'), ('13', '070', 'TLAHUELILPAN'),
('13', '071', 'TLAHUILTEPA'), ('13', '072', 'TLANALAPAN'), ('13', '073', 'TLANCHINOL'), ('13', '074', 'TLAXCOAPAN'),
('13', '075', 'TOLCAYUCA'), ('13', '076', 'TULA DE ALLENDE'), ('13', '077', 'TULANCINGO DE BRAVO'),
('13', '078', 'XOCHIATIPAN'), ('13', '079', 'XOCHICOATLÁN'), ('13', '080', 'YAHUALICA'),
('13', '081', 'ZACUALTIPÁN DE ÁNGELES'), ('13', '082', 'ZAPOTLÁN DE JUÁREZ'), ('13', '083', 'ZEMPOALA'),
('13', '084', 'ZIMAPÁN'),
('09', '002', 'AZCAPOTZALCO'), ('09', '003', 'COYOACÁN'), ('09', '015', 'CUAUHTÉMOC'),
('15', '054', 'METEPEC'), ('15', '106', 'TOLUCA'),
('21', '114', 'PUEBLA'), ('30', '087', 'XALAPA');

CREATE TABLE cat_localidades (
    cve_ent CHAR(2) NOT NULL,
    cve_mun CHAR(3) NOT NULL,
    cve_loc CHAR(4) NOT NULL,
    descripcion VARCHAR(100) NOT NULL,
    PRIMARY KEY (cve_ent, cve_mun, cve_loc),
    FOREIGN KEY (cve_ent, cve_mun) REFERENCES cat_municipios (cve_ent, cve_mun)
);
-- Muestra: sin localidades en el catálogo, el front deja el campo deshabilitado
INSERT INTO cat_localidades VALUES
('13', '048', '0001', 'PACHUCA DE SOTO'), ('13', '048', '0015', 'SANTIAGO TLAPACOYA'),
('13', '048', '0028', 'EL HUIXMÍ'),
('13', '051', '0001', 'PACHUQUILLA'), ('13', '051', '0012', 'LA PROVIDENCIA'),
('13', '077', '0001', 'TULANCINGO'), ('13', '077', '0019', 'JALTEPEC'),
('13', '030', '0001', 'IXMIQUILPAN'), ('13', '069', '0001', 'TIZAYUCA'),
('13', '076', '0001', 'TULA DE ALLENDE'), ('15', '106', '0001', 'TOLUCA DE LERDO');

CREATE TABLE cat_movimientos (cve CHAR(2) PRIMARY KEY, descripcion VARCHAR(80) NOT NULL);
INSERT INTO cat_movimientos VALUES ('01', 'ALTA'), ('02', 'ALTA DE PLAZA ADICIONAL');

CREATE TABLE cat_motivos (
    cve_movimiento CHAR(2) NOT NULL REFERENCES cat_movimientos(cve),
    cve_motivo CHAR(2) NOT NULL,
    descripcion VARCHAR(100) NOT NULL,
    PRIMARY KEY (cve_movimiento, cve_motivo)
);
INSERT INTO cat_motivos VALUES
('01', '01', 'DEFINITIVA'), ('01', '09', 'INTERINA LIMITADA'), ('01', '20', 'PROVISIONAL'),
('01', '95', 'REINGRESO'),
('02', '01', 'PLAZA ADICIONAL DEFINITIVA'), ('02', '09', 'PLAZA ADICIONAL INTERINA LIMITADA'),
('02', '20', 'PLAZA ADICIONAL PROVISIONAL');

CREATE TABLE cat_rechazos (id INTEGER PRIMARY KEY, descripcion VARCHAR(120) NOT NULL);
INSERT INTO cat_rechazos VALUES
(1, 'DATOS PERSONALES INCORRECTOS'), (2, 'RFC O CURP NO COINCIDEN CON EL DOCUMENTO'),
(3, 'CLABE INCORRECTA O SIN DOCUMENTO'), (4, 'PLAZA INCORRECTA'), (5, 'CENTRO DE TRABAJO INCORRECTO'),
(6, 'MOTIVO DEL MOVIMIENTO INCORRECTO'), (7, 'QUINCENAS DE EFECTOS INCORRECTAS'),
(8, 'FALTA DOCUMENTACIÓN SOPORTE'), (9, 'INCOMPATIBILIDAD DE EMPLEO'), (10, 'PLAZA SIN TECHO PRESUPUESTAL'),
(11, 'MOVIMIENTO DUPLICADO'), (12, 'FIRMA FUERA DE TIEMPO'), (13, 'OTRO');

-- Centro de trabajo y plazas vacantes para capturar

INSERT INTO demo_work_centers (code, name, level_name, shift_name, locality, municipality, status_id, status_name, details) VALUES
('13DPR0100X', 'ESCUELA PRIMARIA DEMO HIDALGO', 'Primaria', 'Matutino', 'PACHUCA DE SOTO', 'PACHUCA DE SOTO', 1, 'Activo',
 '{"clave":"13DPR0100X","catCctId":3,"nombre":"ESCUELA PRIMARIA DEMO HIDALGO","tipo":"Escuela","nivel":"Primaria","subnivel":"General","servicio":"Educación básica","sostenimiento":"Público","dependenciaAdministrativa":"Estatal","domicilio":"AV. REVOLUCIÓN","numeroExterior":"300","entreCalle":"NORTE","yCalle":"SUR","colonia":"PERIODISTAS","localidad":"PACHUCA DE SOTO","municipio":"PACHUCA DE SOTO","codigoPostal":"42060","turnoId":"M","turno":"Matutino","zonaEscolar":"03","cctZona":"13DPR0003C","sector":"03","cctSector":"13DPR0003C","servicioRegional":"Regional demo","director":"DIRECTOR HIDALGO DEMO","lada":"771","telefono":"2222222","correo":"hidalgo@example.invalid","paginaWeb":null,"estatusId":1,"estatus":"Activo","fechaAlta":"2019-08-01","fechaClausura":null,"fechaReapertura":null,"fechaCambio":"2026-01-01"}');

INSERT INTO demo_positions (id, code, category_code, center_code, hours, status_id, status_name, unit_code, subunit_code,
                            jornada_hours, max_week_hours, max_class_hours, summary, details, compatibility)
SELECT v.id, v.code, v.category, v.center, v.hours, v.status_id, v.status_name, 'U003', '003', 40, 42, 30,
       jsonb_build_object('id', v.id, 'cvePlaza', v.code, 'categoria', v.category, 'horas', v.hours,
                          'centroTrabajo', v.center, 'estatus', v.status_name),
       jsonb_build_object('id', v.id, 'cvePlaza', v.code, 'codPago', '01', 'unidad', 'U003', 'subunidad', '003',
                          'categoria', v.category, 'categoriaDescripcion', c.description, 'horas', v.hours,
                          'consPlaza', v.id, 'centroTrabajo', v.center, 'estatusId', v.status_id,
                          'estatus', v.status_name, 'codigoCreacion', 'DEMO', 'qnaIni', 202601, 'qnaFin', NULL,
                          'folio', 'DEMO-' || v.id, 'fechaFolio', '2026-01-15'),
       '{"horasJornada":40,"hsemax":42,"hclmax":30}'::jsonb
FROM (VALUES
    (2004, '13DPR0004A004', 'E0721', '13DST0001A', 20, 2, 'Vacante'),
    (2005, '13DPR0005A005', 'E0721', '13DST0002B', 20, 2, 'Vacante'),
    (2006, '13DPR0006A006', 'P0101', '13DST0001A', 40, 2, 'Vacante'),
    (2007, '13DPR0007B007', 'E0721', '13DPR0100X', 20, 2, 'Vacante'),
    (2008, '13DPR0008B008', 'P0101', '13DPR0100X', 40, 3, 'En trámite'),
    (2009, '13DPR0009B009', 'E0721', '13DPR0100X', 20, 3, 'En trámite'),
    (2010, '13DPR0010B010', 'E0721', '13DPR0100X', 20, 2, 'Vacante')
) AS v(id, code, category, center, hours, status_id, status_name)
JOIN demo_categories c ON c.code = v.category;

-- Datos de FUP de los empleados (demo_employees guarda lo que muestran las Consultas)

CREATE SEQUENCE demo_employee_seq START 5001;

CREATE TABLE fup_empleados (
    employee_id INTEGER PRIMARY KEY REFERENCES demo_employees(id) ON DELETE CASCADE,
    primer_apellido VARCHAR(60) NOT NULL,
    segundo_apellido VARCHAR(60),
    nombre VARCHAR(80) NOT NULL,
    cat_estado_civil_id INTEGER REFERENCES cat_estado_civil(id),
    cat_nivel_academico_id INTEGER REFERENCES cat_nivel_academico(id),
    telefono VARCHAR(10),
    cat_tipo_telefono_id INTEGER REFERENCES cat_tipo_telefono(id),
    correo VARCHAR(160),
    clabe VARCHAR(18),
    cve_ent_nacimiento CHAR(2) REFERENCES cat_entidades(cve),
    cp_sat CHAR(5),
    qnaini INTEGER
);

CREATE TABLE fup_domicilios (
    employee_id INTEGER PRIMARY KEY REFERENCES demo_employees(id) ON DELETE CASCADE,
    calle VARCHAR(120) NOT NULL,
    num_ext VARCHAR(20),
    num_int VARCHAR(20),
    colonia VARCHAR(120),
    cve_ent CHAR(2) NOT NULL,
    cve_mun CHAR(3) NOT NULL,
    cve_loc CHAR(4),
    cp CHAR(5) NOT NULL,
    cat_tipo_domicilio_id INTEGER REFERENCES cat_tipo_domicilio(id),
    FOREIGN KEY (cve_ent, cve_mun) REFERENCES cat_municipios (cve_ent, cve_mun)
);

INSERT INTO fup_empleados VALUES
(1001, 'DEMO', 'LOPEZ', 'JUAN', 1, 7, '7711234567', 1, 'juan.demo@example.invalid', '002180700000000011', '13', '42000', 201901),
(1002, 'EJEMPLO', 'CRUZ', 'MARIA', 2, 14, '7717654321', 1, NULL, '012180001234567899', '09', '42080', 201801),
(1003, 'INACTIVA', 'PRUEBA', 'ANA', 1, 7, '7710000000', 2, 'ana.prueba@example.invalid', '072180000000000000', '13', '42000', 201001);

INSERT INTO fup_domicilios VALUES
(1003, 'AV. JUÁREZ', '100', NULL, 'CENTRO', '13', '048', '0001', '42000', 1);

-- Folios

CREATE TABLE fup_control (
    cat_nivel_id INTEGER PRIMARY KEY REFERENCES cat_nivel_rh(id),
    folio_ini INTEGER NOT NULL,
    folio_fin INTEGER NOT NULL,
    ultimo INTEGER NOT NULL
);
-- El nivel 4 (oficinas centrales) no tiene rango: no se ofrece al capturar
INSERT INTO fup_control VALUES
(1, 100000, 199999, 100001),
(2, 200000, 299999, 200000),
(3, 300000, 399999, 300000);

CREATE TABLE fup_folios (
    acuerdo INTEGER PRIMARY KEY,
    status CHAR(1) NOT NULL CHECK (status IN ('A', 'F', 'P')),
    rechazado BOOLEAN NOT NULL DEFAULT FALSE,
    ts_captura TIMESTAMP NOT NULL,
    cat_nivel_id INTEGER REFERENCES cat_nivel_rh(id),
    empleado_id INTEGER,
    crea_empleado BOOLEAN NOT NULL DEFAULT FALSE,
    capturo_user_id INTEGER,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE fup_movimientos (
    id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    acuerdo INTEGER NOT NULL REFERENCES fup_folios(acuerdo),
    orden INTEGER NOT NULL,
    tab_plaza_id INTEGER NOT NULL REFERENCES demo_positions(id),
    cct_clave VARCHAR(10) NOT NULL,
    cve_movimiento CHAR(2) NOT NULL,
    cve_motivo CHAR(2) NOT NULL,
    qna_ini INTEGER NOT NULL,
    qna_fin INTEGER NOT NULL,
    -- Foto previa: cómo estaba la plaza antes de la captura, para restaurarla en la baja
    con_foto BOOLEAN NOT NULL,
    prev_status_id INTEGER,
    prev_status_name VARCHAR(40),
    prev_center_code VARCHAR(10)
);

CREATE TABLE fup_firmas (
    acuerdo INTEGER NOT NULL REFERENCES fup_folios(acuerdo),
    etapa VARCHAR(30) NOT NULL REFERENCES fup_etapas(etapa),
    fecha TIMESTAMP NOT NULL,
    user_id INTEGER,
    PRIMARY KEY (acuerdo, etapa)
);

CREATE TABLE fup_rechazos (
    id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    acuerdo INTEGER NOT NULL REFERENCES fup_folios(acuerdo),
    fecha TIMESTAMP,
    cat_rechazos_id INTEGER REFERENCES cat_rechazos(id),
    observacion VARCHAR(255),
    user_id INTEGER
);

CREATE TABLE fup_clabe_pdf (
    acuerdo INTEGER PRIMARY KEY REFERENCES fup_folios(acuerdo),
    nombre VARCHAR(200),
    contenido BYTEA NOT NULL
);

-- Folio vigente: reingreso de ANA (1003), registro firmado, espera en las demás etapas
INSERT INTO fup_folios (acuerdo, status, rechazado, ts_captura, cat_nivel_id, empleado_id, crea_empleado, capturo_user_id)
VALUES (100001, 'F', TRUE, now() - INTERVAL '3 days', 1, 1003, FALSE, 1002);
INSERT INTO fup_movimientos (acuerdo, orden, tab_plaza_id, cct_clave, cve_movimiento, cve_motivo, qna_ini, qna_fin,
                             con_foto, prev_status_id, prev_status_name, prev_center_code)
VALUES (100001, 1, 2008, '13DPR0100X', '01', '95', 202620, 999999, TRUE, 2, 'Vacante', '13DPR0100X');
INSERT INTO fup_firmas VALUES (100001, 'registro', now() - INTERVAL '2 days', 1002);
INSERT INTO fup_rechazos (acuerdo, fecha, cat_rechazos_id, observacion, user_id)
VALUES (100001, now() - INTERVAL '2 days 12 hours', 7, 'La quincena de inicio no corresponde al nombramiento.', 1004);

-- Folio del legacy: sin fecha real de captura ni foto previa (no se puede dar de baja)
INSERT INTO fup_folios (acuerdo, status, rechazado, ts_captura, cat_nivel_id, empleado_id, crea_empleado)
VALUES (90010, 'F', FALSE, TIMESTAMP '1900-01-01 00:00:00', 1, 1002, FALSE);
INSERT INTO fup_movimientos (acuerdo, orden, tab_plaza_id, cct_clave, cve_movimiento, cve_motivo, qna_ini, qna_fin, con_foto)
VALUES (90010, 1, 2009, '13DPR0100X', '02', '09', 202601, 202624, FALSE);
INSERT INTO fup_firmas VALUES
(90010, 'registro', TIMESTAMP '1900-01-01 00:00:00', NULL),
(90010, 'propone', TIMESTAMP '1900-01-01 00:00:00', NULL);
