package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.LoginRequest;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.exception.InvalidRefreshTokenException;
import ba.tfb.tasknest.security.RefreshTokenCookie;
import ba.tfb.tasknest.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountEndpointTest extends AbstractIntegrationTest {

    private static final String EMAIL = "account@test.ba";

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;

    private AuthResponse user;

    @BeforeEach
    void setUp() {
        user = authService.register(new RegisterRequest(EMAIL, "password123", "Emina", "Begić", "061 111 222"));
    }

    @Test
    @DisplayName("The account page shows the user's own details")
    void get_showsTheAccount() throws Exception {
        mockMvc.perform(get("/api/account").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.firstName").value("Emina"))
                .andExpect(jsonPath("$.lastName").value("Begić"))
                .andExpect(jsonPath("$.phone").value("061 111 222"))
                .andExpect(jsonPath("$.memberSince").exists());
    }

    @Test
    @DisplayName("A changed name shows up in the next session, and an empty phone is removed")
    void update_changesNameAndPhone() throws Exception {
        // Act
        mockMvc.perform(put("/api/account").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": " Emina ", "lastName": "Begić-Hasić", "phone": "  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Emina"))
                .andExpect(jsonPath("$.lastName").value("Begić-Hasić"))
                .andExpect(jsonPath("$.phone").doesNotExist());

        // Assert
        assertThat(authService.refresh(user.refreshToken()).fullName()).isEqualTo("Emina Begić-Hasić");
    }

    @Test
    @DisplayName("A name cannot be emptied")
    void update_refusesAnEmptyName() throws Exception {
        mockMvc.perform(put("/api/account").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "", "lastName": "Begić"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("firstName")));
    }

    @Test
    @DisplayName("After a password change only the new password works and other sessions are signed out")
    void changePassword_replacesThePasswordAndSignsOutOtherSessions() throws Exception {
        // Arrange
        AuthResponse otherDevice = authService.login(new LoginRequest(EMAIL, "password123"));

        // Act
        MvcResult result = changePassword("password123", "new-password-1")
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString(RefreshTokenCookie.NAME + "=")))
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        // Assert
        assertThat(authService.login(new LoginRequest(EMAIL, "new-password-1")).userId()).isEqualTo(user.userId());
        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "password123")))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> authService.refresh(otherDevice.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> authService.refresh(user.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        String newRefreshToken = result.getResponse().getCookie(RefreshTokenCookie.NAME).getValue();
        assertThat(authService.refresh(newRefreshToken).userId())
                .as("an old device knocking with its token must not sign out the device that changed the password")
                .isEqualTo(user.userId());
    }

    @Test
    @DisplayName("A wrong current password changes nothing")
    void changePassword_refusesAWrongCurrentPassword() throws Exception {
        changePassword("not-my-password", "new-password-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The current password is not correct"));

        assertThat(authService.login(new LoginRequest(EMAIL, "password123")).userId()).isEqualTo(user.userId());
    }

    @Test
    @DisplayName("The new password has to differ from the current one and have at least 8 characters")
    void changePassword_refusesAWeakOrUnchangedPassword() throws Exception {
        changePassword("password123", "password123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The new password must be different from the current one"));
        changePassword("password123", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("newPassword")));
    }

    @Test
    @DisplayName("Guessing the current password is stopped after five wrong tries, like a login")
    void changePassword_isThrottledAfterFiveWrongPasswords() throws Exception {
        // Arrange
        for (int i = 0; i < 5; i++) {
            changePassword("guess-" + i, "new-password-1").andExpect(status().isBadRequest());
        }

        // Act & Assert
        changePassword("password123", "new-password-1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("Without a token the account cannot be read or changed")
    void account_requiresALogin() throws Exception {
        mockMvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/account/password").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "password123", "newPassword": "new-password-1"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions changePassword(String current, String next) throws Exception {
        return mockMvc.perform(post("/api/account/password").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "%s", "newPassword": "%s"}
                        """.formatted(current, next)));
    }

    private static String bearer(AuthResponse auth) {
        return "Bearer " + auth.token();
    }
}
