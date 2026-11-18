package mx.gob.seph.smp.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GestionTests extends ApiTestSupport {

    @Test
    void sinElModuloResponde403() throws Exception {
        String capturista = login("capturista");
        mockMvc.perform(get("/users").header("Authorization", capturista))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/modules").header("Authorization", capturista))
                .andExpect(status().isForbidden());
    }

    @Test
    void altaEdicionYBajaDeUsuario() throws Exception {
        String demo = login("demo");
        mockMvc.perform(get("/users").header("Authorization", demo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.username == 'capturista')].rolesName").value("Capturista"))
                .andExpect(jsonPath("$.data[?(@.username == 'capturista')].areaNombre").value("EDUCACIÓN BÁSICA"));
        mockMvc.perform(get("/users/niveles").header("Authorization", demo))
                .andExpect(jsonPath("$.data.length()").value(4));

        String body = """
                {"user":"prueba@example.invalid","password":"Prueba2026","catEmpleadoId":1002,"area":2,
                 "task":"Pruebas","roles":[2],"extras":[11],"principal":null,"active":true,"isVerified":true}
                """;
        String created = mockMvc.perform(post("/users").header("Authorization", demo)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\"id\":(\\d+).*", "$1");

        mockMvc.perform(post("/users").header("Authorization", demo)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/users/user").header("Authorization", demo).header("userId", id))
                .andExpect(jsonPath("$.data.isVerified").value(true))
                .andExpect(jsonPath("$.data.roles[0]").value(2))
                .andExpect(jsonPath("$.data.extras[0]").value(11));

        mockMvc.perform(patch("/users").header("Authorization", demo).header("userId", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"catEmpleadoId":1002,"area":null,"task":null,"roles":[3],"extras":[],
                                 "principal":"/consultas/empleado","active":true,"isVerified":false}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/users/user").header("Authorization", demo).header("userId", id))
                .andExpect(jsonPath("$.data.area").doesNotExist())
                .andExpect(jsonPath("$.data.task").value("Pruebas"))
                .andExpect(jsonPath("$.data.roles[0]").value(3))
                .andExpect(jsonPath("$.data.isVerified").value(false));

        login("prueba@example.invalid", "Prueba2026");

        mockMvc.perform(patch("/users/softdeleted").header("Authorization", demo).header("userId", id))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/users/softdeleted").header("Authorization", demo).header("userId", "1001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No puedes eliminar tu propio usuario."));
    }

    @Test
    void rolConModulosYEtapas() throws Exception {
        String demo = login("demo");
        String created = mockMvc.perform(post("/roles").header("Authorization", demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Consulta","description":"Solo lectura","modulos":[
                                  {"moduleId":11,"agregar":false,"editar":false,"eliminar":false,"autorizar":false,
                                   "verDetalleTecnico":true}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\"id\":(\\d+).*", "$1");

        mockMvc.perform(get("/roles/role").header("Authorization", demo).header("roleId", id))
                .andExpect(jsonPath("$.data.modules[0].id").value(11))
                .andExpect(jsonPath("$.data.modules[0].verDetalleTecnico").value(true));

        mockMvc.perform(put("/roles/etapas").header("Authorization", demo).header("roleId", id)
                        .contentType(MediaType.APPLICATION_JSON).content("[\"registro\",\"sane\"]"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/roles/etapas").header("Authorization", demo).header("roleId", id))
                .andExpect(jsonPath("$.data.length()").value(10))
                .andExpect(jsonPath("$.data[0].etapa").value("registro"))
                .andExpect(jsonPath("$.data[0].asignada").value(true))
                .andExpect(jsonPath("$.data[1].opcional").value(true))
                .andExpect(jsonPath("$.data[3].asignada").value(false));
        mockMvc.perform(put("/roles/etapas").header("Authorization", demo).header("roleId", id)
                        .contentType(MediaType.APPLICATION_JSON).content("[\"no-existe\"]"))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/roles/softdeleted").header("Authorization", demo).header("roleId", id))
                .andExpect(status().isOk());
    }

    @Test
    void modulosNoPermitenBorrarLosDeSistema() throws Exception {
        String demo = login("demo");
        mockMvc.perform(get("/icons").header("Authorization", demo))
                .andExpect(jsonPath("$.data[0].materialIcon").exists());
        mockMvc.perform(get("/modules/module").header("Authorization", demo).header("moduleId", "22"))
                .andExpect(jsonPath("$.data.codigo").value("MOV_NUEVO_INGRESO"))
                .andExpect(jsonPath("$.data.movimientos.length()").value(2));

        String created = mockMvc.perform(post("/modules").header("Authorization", demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Reportes","codigo":"rep_demo","path":"/reportes","description":null,
                                 "visible":true,"iconId":5,"parentId":10,"orden":9,"rolesId":[1],
                                 "movimientos":[],"todosLosMovimientos":false}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\"id\":(\\d+).*", "$1");
        mockMvc.perform(get("/modules/module").header("Authorization", demo).header("moduleId", id))
                .andExpect(jsonPath("$.data.codigo").value("REP_DEMO"))
                .andExpect(jsonPath("$.data.roles[0].id").value(1));

        mockMvc.perform(patch("/modules/softdeleted").header("Authorization", demo).header("moduleId", id))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/modules/softdeleted").header("Authorization", demo).header("moduleId", "2"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/modules/softdeleted").header("Authorization", demo).header("moduleId", "1"))
                .andExpect(status().isConflict());
    }
}
