package mx.gob.seph.smp.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "demo.mail.codigo-fijo=123456")
@AutoConfigureMockMvc
@Transactional
class CuentaTests extends ApiTestSupport {
    @Autowired
    JdbcClient jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void cuentaNuevaVerificaElCorreoYCambiaLaContrasena() throws Exception {
        String authorization = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\":\"nuevo\",\"password\":\"Demo123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(false))
                .andExpect(jsonPath("$.data.isPassword").value(false))
                .andReturn().getResponse().getHeader("Authorization");

        mockMvc.perform(post("/users/validateToken").header("Authorization", authorization).header("token", "000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(post("/users/validateToken").header("Authorization", authorization).header("token", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String body = mockMvc.perform(post("/users/changePassword")
                        .header("Authorization", authorization).header("password", "OtraClave2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn().getResponse().getContentAsString();
        String token = (String) ((Map<?, ?>) objectMapper.readValue(body, Map.class).get("data")).get("token");

        // El cambio invalida el token anterior
        mockMvc.perform(get("/users/me").header("Authorization", authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sesión cerrada"));
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("nuevo"));

        // Solo se puede una vez
        mockMvc.perform(post("/users/changePassword")
                        .header("Authorization", "Bearer " + token).header("password", "OtraClave2027"))
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\":\"nuevo\",\"password\":\"OtraClave2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.isPassword").value(true))
                .andExpect(jsonPath("$.data.config.principal").value("/dashboard"));
    }

    @Test
    void recuperacionDeContrasenaConLaLiga() throws Exception {
        mockMvc.perform(post("/users/sendChangePassword").header("username", "capturista"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        // Mismo aviso aunque el usuario no exista
        mockMvc.perform(post("/users/sendChangePassword").header("username", "no-existe"))
                .andExpect(jsonPath("$.success").value(true));

        Map<String, Object> liga = jdbc.sql("""
                        SELECT r.folio, r.ts FROM core_password_recovery r
                        JOIN core_users u ON u.id = r.user_id WHERE u.username = 'capturista'
                        """)
                .query().singleRow();
        String folio = (String) liga.get("folio");
        String ts = (String) liga.get("ts");

        mockMvc.perform(get("/users/validatePasswordRecovery").header("token", folio).header("ts", ts))
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get("/users/validatePasswordRecovery").header("token", folio).header("ts", "1"))
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(post("/users/changePasswordRecovery")
                        .header("token", folio).header("ts", ts).header("password", "Recuperada2026"))
                .andExpect(jsonPath("$.success").value(true));
        // La liga ya se usó
        mockMvc.perform(get("/users/validatePasswordRecovery").header("token", folio).header("ts", ts))
                .andExpect(jsonPath("$.success").value(false));

        login("capturista", "Recuperada2026");
    }
}
