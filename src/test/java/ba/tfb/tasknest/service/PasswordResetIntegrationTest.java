package ba.tfb.tasknest.service;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.LoginRequest;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.exception.InvalidRefreshTokenException;
import ba.tfb.tasknest.messaging.PasswordResetMailer;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.repository.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PasswordResetIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository tokenRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean private PasswordResetMailer mailer;

    private AuthResponse amra;

    @BeforeEach
    void setUp() {
        amra = authService.register(new RegisterRequest("reset@test.ba", "old-password", "Amra", "Hodžić", null));
    }

    @Test
    @DisplayName("The emailed link sets a new password once, and every existing session is signed out")
    void resetChangesThePasswordAndEndsSessions() throws Exception {
        // Arrange
        requestReset("Reset@Test.ba").andExpect(status().isAccepted());
        String token = sentToken(1);

        // Act
        confirm(token, "new-password-1").andExpect(status().isNoContent());

        // Assert
        assertThatThrownBy(() -> authService.login(new LoginRequest("reset@test.ba", "old-password")))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(authService.login(new LoginRequest("reset@test.ba", "new-password-1")).token()).isNotBlank();
        assertThatThrownBy(() -> authService.refresh(amra.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        confirm(token, "another-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This reset link is invalid or has expired. Ask for a new one."));
    }

    @Test
    @DisplayName("An unknown email gets the same answer and no mail, so nobody can probe which accounts exist")
    void unknownEmailLooksTheSame() throws Exception {
        requestReset("nobody@test.ba").andExpect(status().isAccepted());

        verify(mailer, never()).sendResetLink(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("A suspended account gets no reset link")
    void suspendedAccountGetsNoLink() throws Exception {
        // Arrange
        transactionTemplate.executeWithoutResult(status ->
                userRepository.findById(amra.userId()).orElseThrow().setAccountStatus(AccountStatus.SUSPENDED));

        // Act
        requestReset("reset@test.ba").andExpect(status().isAccepted());

        // Assert
        verify(mailer, never()).sendResetLink(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Asking again makes the previous link useless")
    void newRequestInvalidatesTheOldLink() throws Exception {
        // Arrange
        requestReset("reset@test.ba");
        requestReset("reset@test.ba");
        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(mailer, times(2)).sendResetLink(eq("reset@test.ba"), eq("Amra"), tokens.capture(), anyLong());

        // Act & Assert
        confirm(tokens.getAllValues().get(0), "new-password-1").andExpect(status().isBadRequest());
        confirm(tokens.getAllValues().get(1), "new-password-1").andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("An expired link is refused")
    void expiredLinkIsRefused() throws Exception {
        // Arrange
        requestReset("reset@test.ba");
        String token = sentToken(1);
        transactionTemplate.executeWithoutResult(status -> tokenRepository.findAll()
                .forEach(stored -> stored.setExpiresAt(LocalDateTime.now().minusMinutes(1))));

        // Act & Assert
        confirm(token, "new-password-1").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("At most three links per hour are sent to one email, and the answer never changes")
    void limitsHowManyLinksAreSent() throws Exception {
        for (int i = 0; i < 5; i++) {
            requestReset("reset@test.ba").andExpect(status().isAccepted());
        }

        verify(mailer, times(3)).sendResetLink(eq("reset@test.ba"), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("The new password follows the same rules as at sign-up")
    void shortPasswordIsRefused() throws Exception {
        // Arrange
        requestReset("reset@test.ba");
        String token = sentToken(1);

        // Act & Assert
        confirm(token, "short").andExpect(status().isBadRequest());
        assertThat(authService.login(new LoginRequest("reset@test.ba", "old-password")).token()).isNotBlank();
    }

    private String sentToken(int expectedMails) {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailer, times(expectedMails)).sendResetLink(eq("reset@test.ba"), eq("Amra"), token.capture(), eq(30L));
        return token.getValue();
    }

    private ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}
                        """.formatted(email)));
    }

    private ResultActions confirm(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s", "password": "%s"}
                        """.formatted(token, password)));
    }
}
