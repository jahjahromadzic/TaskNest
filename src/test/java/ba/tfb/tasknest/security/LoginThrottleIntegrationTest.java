package ba.tfb.tasknest.security;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LoginThrottleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;

    @BeforeEach
    void setUp() {
        authService.register(new RegisterRequest("throttle@test.ba", "password123", "Amra", "Hodžić", null));
        authService.register(new RegisterRequest("other@test.ba", "password123", "Haris", "Mehić", null));
    }

    @Test
    @DisplayName("After five wrong passwords even the right one is refused for a while, with a 429 and Retry-After")
    void blocksAnEmailAfterFiveFailures() throws Exception {
        // Arrange
        for (int i = 0; i < 5; i++) {
            login("throttle@test.ba", "wrong-password").andExpect(status().isUnauthorized());
        }

        // Act & Assert
        login("throttle@test.ba", "password123")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail", containsString("Too many failed login attempts. Try again in 15 min.")));
        login("THROTTLE@test.ba ", "password123").andExpect(status().isTooManyRequests());
        login("other@test.ba", "password123").andExpect(status().isOk());
    }

    @Test
    @DisplayName("A successful login wipes the earlier failures of that account")
    void successfulLoginResetsTheCount() throws Exception {
        // Arrange
        for (int i = 0; i < 4; i++) {
            login("throttle@test.ba", "wrong-password").andExpect(status().isUnauthorized());
        }
        login("throttle@test.ba", "password123").andExpect(status().isOk());

        // Act
        for (int i = 0; i < 4; i++) {
            login("throttle@test.ba", "wrong-password").andExpect(status().isUnauthorized());
        }

        // Assert
        login("throttle@test.ba", "password123").andExpect(status().isOk());
    }

    @Test
    @DisplayName("One address guessing many accounts is stopped after twenty failures")
    void blocksAnAddressThatTriesManyAccounts() throws Exception {
        // Arrange
        for (int i = 0; i < 20; i++) {
            login("guess" + i + "@test.ba", "wrong-password").andExpect(status().isUnauthorized());
        }

        // Act & Assert
        login("other@test.ba", "password123").andExpect(status().isTooManyRequests());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }
}
