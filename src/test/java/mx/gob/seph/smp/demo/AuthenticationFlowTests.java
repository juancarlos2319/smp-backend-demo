package mx.gob.seph.smp.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationFlowTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginProvidesSessionAndAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"user":"demo","password":"Demo123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1001))
                .andExpect(jsonPath("$.data.roles[0]").value(1))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.config.principal").value("/dashboard"));
    }

    @Test
    void invalidCredentialsAreRejected() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"user":"demo","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void modulesRequireAnActiveTokenAndLogoutRevokesIt() throws Exception {
        MvcResult login = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"user":"demo","password":"Demo123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String authorization = login.getResponse().getHeader("Authorization");

        mockMvc.perform(get("/users/moduleByUser").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.path == '/consultas/empleado')]").exists())
                .andExpect(jsonPath("$.data[?(@.codigo == 'GESTION_USUARIOS')].permisos.agregar").value(true));

        mockMvc.perform(post("/users/logout").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Token revocado: el front avisa "Sesión terminada" con este mensaje
        mockMvc.perform(get("/users/moduleByUser").header("Authorization", authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Sesión cerrada"));
    }

    @Test
    void moduleCatalogRejectsRequestsWithoutToken() throws Exception {
        mockMvc.perform(get("/users/moduleByUser"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void consultationEndpointsReturnRelatedSyntheticDataAndHonorFilters() throws Exception {
        String authorization = loginAuthorization();

        mockMvc.perform(get("/empleados/opciones").param("q", "juan").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1001));
        mockMvc.perform(get("/consultas/empleados").param("rfc", "DLOJ").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/consultas/empleados/1001").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rfc").value("DLOJ900101AB1"));
        mockMvc.perform(get("/consultas/empleados/1001/plazas").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(get("/consultas/empleados/1001/historial").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("P"));
        mockMvc.perform(get("/consultas/compatibilidad/1001").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPlazas").value(2))
                .andExpect(jsonPath("$.data.unidades[0].plazas[0].cvePlaza").exists());

        mockMvc.perform(get("/consultas/plazas").param("clave", "13DPR0001").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(2001));
        mockMvc.perform(get("/consultas/plazas/2001").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cvePlaza").value("13DPR0001A001"));
        mockMvc.perform(get("/consultas/plazas/2001/ocupantes").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].empleadoId").value(1001));
        mockMvc.perform(get("/consultas/plazas/2001/historial").param("cct", "13DST0001A")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(get("/consultas/centros-trabajo").param("q", "toluca").param("estatus", "1")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].clave").value("13DST0001A"));
        mockMvc.perform(get("/consultas/centros-trabajo/13DST0001A").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nombre").value("ESCUELA DEMO PRIMARIA"));
        mockMvc.perform(get("/consultas/centros-trabajo/13DST0001A/estatus")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].plazas").value(2));
        mockMvc.perform(get("/consultas/centros-trabajo/13DST0001A/plazas").param("estatus", "1")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        mockMvc.perform(get("/consultas/categorias").param("q", "E0721").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].clave").value("E0721"));
        mockMvc.perform(get("/consultas/categorias/E0721").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hsemax").value(42));
        mockMvc.perform(get("/consultas/categorias/E0721/conceptos")
                        .param("qna", "202622").param("nivelSueldo", "1")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(get("/consultas/conceptos").param("percDed", "D").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].clave").value("D001"));
    }

    @Test
    void consultationEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/empleados/opciones"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/consultas/empleados"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingConsultationRecordsUseApiErrorEnvelope() throws Exception {
        mockMvc.perform(get("/consultas/empleados/9999")
                        .header("Authorization", loginAuthorization()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    private String loginAuthorization() throws Exception {
        MvcResult login = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"user":"demo","password":"Demo123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getHeader("Authorization");
    }
}
