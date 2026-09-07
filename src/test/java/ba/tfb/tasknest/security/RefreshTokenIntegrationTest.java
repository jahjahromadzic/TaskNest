package ba.tfb.tasknest.security;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.entity.RefreshToken;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.repository.RefreshTokenRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import ba.tfb.tasknest.exception.InvalidRefreshTokenException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh tokeni: izdavanje, rotacija, opoziv i granicni slucajevi.
 */
@AutoConfigureMockMvc
class RefreshTokenIntegrationTest extends AbstractIntegrationTest {

    /** Zasticena putanja bez kontrolera: 401 bez autentikacije, 404 sa njom. */
    private static final String PROTECTED_PROBE = "/api/__auth_probe";

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @AfterEach
    void tearDown() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- izdavanje i rotacija ----------

    @Test
    @DisplayName("Registration issues both an access token and a refresh token")
    void registrationIssuesBothTokens() {
        AuthResponse response = register("issued@test.ba");

        assertNotNull(response.token(), "access token missing");
        assertNotNull(response.refreshToken(), "refresh token missing");
        assertTrue(response.expiresIn() > 0, "expiresIn must tell the client when to refresh");
        assertEquals(1, refreshTokenRepository.count());
    }

    @Test
    @DisplayName("Refreshing with a valid token returns a new pair")
    void refreshWithValidTokenReturnsNewPair() {
        AuthResponse initial = register("refresh.ok@test.ba");

        AuthResponse refreshed = authService.refresh(initial.refreshToken());

        assertNotNull(refreshed.token(), "a new access token must be issued");
        assertNotNull(refreshed.refreshToken());
        assertNotEquals(initial.refreshToken(), refreshed.refreshToken(),
                "rotation must hand out a different refresh token");
        assertEquals(initial.userId(), refreshed.userId());
        // Access token se namjerno ne poredi: dva JWT-a izdata istom korisniku unutar
        // iste sekunde su bajt-identicna, jer iat/exp imaju rezoluciju od sekunde.
    }

    @Test
    @DisplayName("After rotation the previous refresh token no longer works")
    void rotatedRefreshTokenIsDead() {
        AuthResponse initial = register("rotated@test.ba");
        authService.refresh(initial.refreshToken());

        assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh(initial.refreshToken()),
                "the consumed refresh token must be rejected");
    }

    @Test
    @DisplayName("Replaying a consumed token revokes every token of that user")
    void replayRevokesAllTokensOfThatUser() {
        AuthResponse initial = register("replay@test.ba");
        AuthResponse current = authService.refresh(initial.refreshToken());

        // Stari token stize ponovo - tretira se kao moguca krada.
        assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh(initial.refreshToken()));

        // Posljedica odluke iz handleReuse: i tekuci, do maloprije ispravan token je mrtav.
        assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh(current.refreshToken()),
                "reuse detection must invalidate the whole family, not just the replayed token");

        assertEquals(0, refreshTokenRepository.findAll().stream()
                        .filter(token -> token.getRevokedAt() == null)
                        .count(),
                "no active refresh token may survive reuse detection");
    }

    // ---------- odbijanje ----------

    @Test
    @DisplayName("An expired refresh token is rejected")
    void expiredRefreshTokenIsRejected() {
        AuthResponse initial = register("expired.refresh@test.ba");

        RefreshToken stored = refreshTokenRepository.findByToken(initial.refreshToken()).orElseThrow();
        stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        refreshTokenRepository.saveAndFlush(stored);

        assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh(initial.refreshToken()));
    }

    @Test
    @DisplayName("A revoked refresh token is rejected")
    void revokedRefreshTokenIsRejected() {
        AuthResponse initial = register("revoked.refresh@test.ba");
        authService.logout(initial.refreshToken());

        assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh(initial.refreshToken()));
    }

    @Test
    @DisplayName("An unknown refresh token is rejected")
    void unknownRefreshTokenIsRejected() {
        assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh("this-token-was-never-issued"));
    }

    @Test
    @DisplayName("A suspended user cannot exchange a refresh token")
    void suspendedUserCannotRefresh() {
        AuthResponse initial = register("suspended.refresh@test.ba");
        setAccountStatus(initial.userId(), AccountStatus.SUSPENDED);

        assertThrows(LockedException.class,
                () -> authService.refresh(initial.refreshToken()));
    }

    @Test
    @DisplayName("A refresh token presented as a Bearer access token is rejected")
    void refreshTokenIsNotAcceptedAsAccessToken() throws Exception {
        AuthResponse initial = register("not.a.bearer@test.ba");

        // Refresh token nije JWT, pa parseAccessToken ne prolazi. Cak i da jeste,
        // zaustavio bi ga type claim, koji mora biti "access".
        mockMvc.perform(get(PROTECTED_PROBE)
                        .header("Authorization", "Bearer " + initial.refreshToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An access token is not accepted as a refresh token either")
    void accessTokenIsNotAcceptedAsRefreshToken() {
        AuthResponse initial = register("access.as.refresh@test.ba");

        assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh(initial.token()),
                "an access JWT is not stored in refresh_tokens and must not be exchangeable");
    }

    // ---------- odjava ----------

    @Test
    @DisplayName("Logout revokes the token but keeps the row as an audit trail")
    void logoutRevokesTokenAndKeepsRow() {
        AuthResponse initial = register("logout@test.ba");

        authService.logout(initial.refreshToken());

        RefreshToken stored = refreshTokenRepository.findByToken(initial.refreshToken())
                .orElseThrow(() -> new AssertionError("the row must not be deleted"));
        assertNotNull(stored.getRevokedAt(), "revoked_at must be set");
        assertEquals(1, refreshTokenRepository.count(), "the row is kept for auditing");
    }

    // ---------- kroz HTTP ----------

    @Test
    @DisplayName("POST /api/auth/refresh works end to end and is publicly reachable")
    void refreshEndpointWorksOverHttp() throws Exception {
        AuthResponse initial = register("http.refresh@test.ba");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshTokenJson(initial.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").isNumber());
    }

    @Test
    @DisplayName("POST /api/auth/logout returns 204 and is publicly reachable")
    void logoutEndpointWorksOverHttp() throws Exception {
        AuthResponse initial = register("http.logout@test.ba");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshTokenJson(initial.refreshToken())))
                .andExpect(status().isNoContent());

        RefreshToken stored = refreshTokenRepository.findByToken(initial.refreshToken()).orElseThrow();
        assertNotNull(stored.getRevokedAt());
    }

    // ---------- helpers ----------

    /** Token je base64url, dakle bez znakova koji bi trazili escape u JSON-u. */
    private String refreshTokenJson(String refreshToken) {
        return "{\"refreshToken\":\"" + refreshToken + "\"}";
    }

    private AuthResponse register(String email) {
        return authService.register(
                new RegisterRequest(email, "password123", "Test", "User", null));
    }

    private void setAccountStatus(UUID userId, AccountStatus status) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setAccountStatus(status);
        userRepository.saveAndFlush(user);
    }
}
