package mx.gob.seph.smp.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

/** Nuevo ingreso → registro → rechazo → baja, y el folio 100001 hasta Pagos. Todo se revierte al terminar. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FupTests extends ApiTestSupport {
    private static final String NUEVO_INGRESO = "/fup/movimientos/nuevo-ingreso";

    private static final String DATOS = """
            {"rfc":"PERJ950615AB1","curp":"PERJ950615HHGRRN04","primerApellido":"PEREZ","segundoApellido":"RUIZ",
             "nombre":"JORGE","catEstadoCivilId":1,"catNivelAcademicoId":7,"cveEntNacimiento":"13","cpSat":"42000",
             "domicilio":{"calle":"Calle Uno","numExt":"5","numInt":null,"colonia":"Centro","cveEnt":"13",
                          "cveMun":"048","cveLoc":"0001","cp":"42000","catTipoDomicilioId":1},
             "telefono":"771 123 4567","catTipoTelefonoId":1,"correo":"jorge@example.invalid",
             "clabe":"002180700000000011","catNivelId":1,
             "plazas":[{"tabPlazaId":2004,"catCctId":1,"cveMotivo":"01","qnaIni":202624,"qnaFin":999999},
                       {"tabPlazaId":2007,"catCctId":3,"cveMotivo":"09","qnaIni":202624,"qnaFin":202706}]}
            """;

    @Test
    void catalogosDelCapturista() throws Exception {
        String capturista = login("capturista");
        mockMvc.perform(get(NUEVO_INGRESO + "/catalogos").header("Authorization", capturista))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.puedeRegistrar").value(true))
                .andExpect(jsonPath("$.data.niveles.length()").value(1))
                .andExpect(jsonPath("$.data.entidades.length()").value(32))
                .andExpect(jsonPath("$.data.motivos[0].cve").value("01"))
                .andExpect(jsonPath("$.data.motivosAdicionales.length()").value(3));
        mockMvc.perform(get(NUEVO_INGRESO + "/municipios").param("cveEnt", "13").header("Authorization", capturista))
                .andExpect(jsonPath("$.data.length()").value(84));
        mockMvc.perform(get(NUEVO_INGRESO + "/localidades").param("cveEnt", "13").param("cveMun", "048")
                        .header("Authorization", capturista))
                .andExpect(jsonPath("$.data.length()").value(3));
        mockMvc.perform(get(NUEVO_INGRESO + "/plazas").param("clave", "13DPR0004").header("Authorization", capturista))
                .andExpect(jsonPath("$.data[0].estatus").value("Vacante"))
                .andExpect(jsonPath("$.data[0].catCctId").value(1));
        mockMvc.perform(get(NUEVO_INGRESO + "/plazas").param("clave", "13").header("Authorization", capturista))
                .andExpect(status().isConflict());
        mockMvc.perform(get(NUEVO_INGRESO + "/centros-trabajo").param("q", "13DPR").header("Authorization", capturista))
                .andExpect(jsonPath("$.data[0].catCctId").value(3));

        // El autorizador no tiene el módulo
        mockMvc.perform(get(NUEVO_INGRESO + "/catalogos").header("Authorization", login("autorizador")))
                .andExpect(status().isForbidden());
    }

    @Test
    void busquedaPrevia() throws Exception {
        String capturista = login("capturista");
        mockMvc.perform(get(NUEVO_INGRESO + "/busqueda").param("rfc", "PERJ950615AB1").header("Authorization", capturista))
                .andExpect(jsonPath("$.data.tipo").value("NUEVO_INGRESO"))
                .andExpect(jsonPath("$.data.puedeCapturar").value(true));
        // ANA tiene el folio 100001 en trámite
        mockMvc.perform(get(NUEVO_INGRESO + "/busqueda").param("curp", "IAPA800303MDFNRA03").header("Authorization", capturista))
                .andExpect(jsonPath("$.data.tipo").value("REINGRESO"))
                .andExpect(jsonPath("$.data.puedeCapturar").value(false))
                .andExpect(jsonPath("$.data.empleado.domicilio.cveMun").value("048"))
                .andExpect(jsonPath("$.data.empleado.catSexoId").value(2))
                .andExpect(jsonPath("$.data.foliosEnTramite[0].acuerdo").value(100001));
        // JUAN ocupa dos plazas
        mockMvc.perform(get(NUEVO_INGRESO + "/busqueda").param("rfc", "DLOJ900101AB1").header("Authorization", capturista))
                .andExpect(jsonPath("$.data.altasActivas.length()").value(2))
                .andExpect(jsonPath("$.data.altasActivas[0].qnaFin").value(999999));
    }

    @Test
    void capturaRechazoYBajaDeUnFolio() throws Exception {
        String capturista = login("capturista");
        MockMultipartFile datos = new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE,
                DATOS.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile pdf = new MockMultipartFile("clabePdf", "clabe.pdf", MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4 demo".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart(NUEVO_INGRESO).file(datos).file(pdf).header("Authorization", capturista))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acuerdo").value(100002))
                .andExpect(jsonPath("$.data.tipo").value("NUEVO_INGRESO"))
                .andExpect(jsonPath("$.data.movimientos").value(2));

        // Las plazas quedan en trámite y ya no se pueden capturar
        mockMvc.perform(get("/consultas/plazas/2004").header("Authorization", capturista))
                .andExpect(jsonPath("$.data.estatus").value("En trámite"));
        mockMvc.perform(multipart(NUEVO_INGRESO).file(datos).header("Authorization", capturista))
                .andExpect(status().isConflict());

        // El historial del empleado muestra el folio en trámite
        mockMvc.perform(get("/consultas/empleados").param("rfc", "PERJ").header("Authorization", capturista))
                .andExpect(jsonPath("$.data[0].activo").value(false));

        // Registro lo firma el capturista; el autorizador no puede saltarse propone
        mockMvc.perform(get("/fup/movimientos/autorizacion/pendientes").param("etapa", "registro")
                        .header("Authorization", capturista))
                .andExpect(jsonPath("$.data.folios[?(@.acuerdo == 100002)].puedeAprobar").value(true));
        mockMvc.perform(post("/fup/movimientos/100002/aprobar/registro").header("Authorization", capturista))
                .andExpect(status().isOk());
        String autorizador = login("autorizador");
        mockMvc.perform(post("/fup/movimientos/100002/aprobar/otorga").header("Authorization", autorizador))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Falta firmar la etapa 'propone'."));
        mockMvc.perform(post("/fup/movimientos/100002/rechazar").header("Authorization", autorizador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"catRechazosId\":1}"))
                .andExpect(status().isForbidden());

        String informatica = login("informatica");
        mockMvc.perform(post("/fup/movimientos/100002/rechazar").header("Authorization", informatica)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"catRechazosId\":3,\"observacion\":\"CLABE ilegible\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/fup/movimientos/autorizacion/100002").header("Authorization", informatica))
                .andExpect(jsonPath("$.data.folio.status").value("A"))
                .andExpect(jsonPath("$.data.folio.rechazado").value(true))
                .andExpect(jsonPath("$.data.firmas.length()").value(10))
                .andExpect(jsonPath("$.data.firmas[0].firmada").value(false))
                .andExpect(jsonPath("$.data.rechazos[0].motivoId").value(3))
                .andExpect(jsonPath("$.data.rechazos[0].usuario").value("informatica"));

        // Baja: restaura las plazas y borra al empleado que creó el folio
        mockMvc.perform(get("/fup/movimientos/baja").param("rfc", "PERJ").header("Authorization", capturista))
                .andExpect(jsonPath("$.data[0].acuerdo").value(100002))
                .andExpect(jsonPath("$.data[0].movimientos").value("01/01, 02/09"))
                .andExpect(jsonPath("$.data[0].conFotoPrevia").value(true));
        mockMvc.perform(get("/fup/movimientos/baja/100002").header("Authorization", capturista))
                .andExpect(jsonPath("$.data.puedeDarDeBaja").value(true))
                .andExpect(jsonPath("$.data.borraEmpleado").value(true));
        mockMvc.perform(delete("/fup/movimientos/baja/100002").header("Authorization", capturista))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plazasRestauradas").value(2))
                .andExpect(jsonPath("$.data.empleadoBorrado").value(true));
        mockMvc.perform(get("/consultas/plazas/2004").header("Authorization", capturista))
                .andExpect(jsonPath("$.data.estatus").value("Vacante"));
        mockMvc.perform(get("/fup/movimientos/baja/100002").header("Authorization", capturista))
                .andExpect(status().isNotFound());
    }

    @Test
    void elFolioDelLegacyNoSePuedeDarDeBaja() throws Exception {
        String capturista = login("capturista");
        mockMvc.perform(get("/fup/movimientos/baja/90010").header("Authorization", capturista))
                .andExpect(jsonPath("$.data.puedeDarDeBaja").value(false))
                .andExpect(jsonPath("$.data.tsCaptura").value(org.hamcrest.Matchers.startsWith("1900-01-01")));
        mockMvc.perform(delete("/fup/movimientos/baja/90010").header("Authorization", capturista))
                .andExpect(status().isConflict());
    }

    @Test
    void elFolio100001LlegaHastaPagos() throws Exception {
        String demo = login("demo");
        mockMvc.perform(get("/fup/movimientos/autorizacion/etapas").header("Authorization", demo))
                .andExpect(jsonPath("$.data.etapas.length()").value(10))
                .andExpect(jsonPath("$.data.puedeRechazar").value(true));
        mockMvc.perform(get("/fup/movimientos/autorizacion/pendientes").param("etapa", "propone")
                        .header("Authorization", demo))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.folios[0].acuerdo").value(100001))
                .andExpect(jsonPath("$.data.folios[0].diasEnEtapa").isNumber());
        // El legacy espera en otorga sin días
        mockMvc.perform(get("/fup/movimientos/autorizacion/pendientes").param("etapa", "otorga")
                        .header("Authorization", demo))
                .andExpect(jsonPath("$.data.folios[0].acuerdo").value(90010))
                .andExpect(jsonPath("$.data.folios[0].diasEnEtapa").doesNotExist());

        for (String etapa : new String[] {"propone", "otorga", "autoriza", "informatica_validar", "fone",
                "informatica", "pagos"}) {
            mockMvc.perform(post("/fup/movimientos/100001/aprobar/" + etapa).header("Authorization", demo))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/fup/movimientos/autorizacion/100001").header("Authorization", demo))
                .andExpect(jsonPath("$.data.folio.status").value("P"));
        mockMvc.perform(get("/consultas/empleados/1003").header("Authorization", demo))
                .andExpect(jsonPath("$.data.activo").value(true));
        mockMvc.perform(get("/consultas/plazas/2008/ocupantes").header("Authorization", demo))
                .andExpect(jsonPath("$.data[0].empleadoId").value(1003));
        mockMvc.perform(get("/consultas/empleados/1003/historial").header("Authorization", demo))
                .andExpect(jsonPath("$.data[?(@.acuerdo == 100001)].status").value("P"));
        mockMvc.perform(post("/fup/movimientos/100001/aprobar/sane").header("Authorization", demo))
                .andExpect(status().isConflict());
    }
}
