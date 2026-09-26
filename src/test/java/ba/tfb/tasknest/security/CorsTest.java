package ba.tfb.tasknest.security;

import ba.tfb.tasknest.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CorsTest extends AbstractIntegrationTest {

    private static final String FRONTEND = "http://localhost:4200";

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("A preflight from the frontend origin is answered, not rejected as unauthenticated")
    void preflight_isAllowed_fromTheFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/conversations")
                        .header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("POST")));
    }

    @Test
    @DisplayName("A preflight from an unknown origin is refused")
    void preflight_isRefused_fromAnUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/conversations")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("A simple request from the frontend origin carries the allow header")
    void simpleRequest_carriesTheAllowHeader() throws Exception {
        mockMvc.perform(get("/api/categories").header("Origin", FRONTEND))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND));
    }
}
