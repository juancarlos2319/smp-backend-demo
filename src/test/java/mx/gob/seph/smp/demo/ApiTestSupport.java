package mx.gob.seph.smp.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Login con los usuarios de las migraciones (contraseña `demo.seed.password`). */
abstract class ApiTestSupport {
    static final String PASSWORD = "Demo123!";

    @Autowired
    MockMvc mockMvc;

    String login(String user) throws Exception {
        return login(user, PASSWORD);
    }

    String login(String user, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\":\"" + user + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getHeader("Authorization");
    }
}
