package ba.tfb.tasknest.security;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.LoginRequest;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.repository.RefreshTokenRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pokriva rupe nadjene u pregledu auth sloja: provjeru statusa naloga na obje
 * putanje, normalizaciju emaila pri registraciji i ponasanje JWT filtera.
 */
@AutoConfigureMockMvc
class AuthIntegrationTest extends AbstractIntegrationTest {

    /**
     * Zasticena putanja koja namjerno nema kontroler. Bez tokena vraca 401, a s
     * ispravnim tokenom 404 - razlika izmedju to dvoje je dokaz da je filter
     * autentikovao zahtjev. Prefiks __ da niko nikad ne mapira kontroler na nju.
     */
    private static final String PROTECTED_PROBE = "/api/__auth_probe";

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private JwtService jwtService;

    /**
     * Spy, ne obican @Autowired: duplikat uhvacen prije upisa i duplikat uhvacen
     * na constraintu daju spolja isti izuzetak i istu poruku, pa se razlikuju
     * jedino po tome da li je INSERT uopste pokusan.
     */
    @MockitoSpyBean private UserRepository userRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @AfterEach
    void tearDown() {
        // Prije korisnika: refresh_tokens ima FK na users.
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- login ----------

    @Test
    @DisplayName("Login with a wrong password is rejected")
    void loginWithWrongPasswordIsRejected() {
        register("wrong.pass@test.ba", "correct-password");

        assertThrows(
                BadCredentialsException.class,
                () -> authService.login(new LoginRequest("wrong.pass@test.ba", "not-the-password"))
        );
    }

    @Test
    @DisplayName("Login on a suspended account is rejected even with the right password")
    void loginOnSuspendedAccountIsRejected() {
        AuthResponse registered = register("suspended@test.ba", "correct-password");
        setAccountStatus(registered.userId(), AccountStatus.SUSPENDED);

        assertThrows(
                LockedException.class,
                () -> authService.login(new LoginRequest("suspended@test.ba", "correct-password"))
        );
    }

    @Test
    @DisplayName("Login on a deactivated account is rejected")
    void loginOnDeactivatedAccountIsRejected() {
        AuthResponse registered = register("deactivated@test.ba", "correct-password");
        setAccountStatus(registered.userId(), AccountStatus.DEACTIVATED);

        assertThrows(
                DisabledException.class,
                () -> authService.login(new LoginRequest("deactivated@test.ba", "correct-password"))
        );
    }

    // ---------- registracija ----------

    @Test
    @DisplayName("Registering the same email twice is rejected with a business error")
    void duplicateRegistrationIsRejected() {
        register("duplicate@test.ba", "password123");

        BusinessRuleException thrown = assertThrows(
                BusinessRuleException.class,
                () -> register("duplicate@test.ba", "password123")
        );
        assertTrue(thrown.getMessage().toLowerCase().contains("already exists"),
                "Expected a duplicate-email message, got: " + thrown.getMessage());
    }

    @Test
    @DisplayName("Duplicate differing only in case is caught by the pre-check, before any insert")
    void duplicateRegistrationInDifferentCaseIsRejectedBeforeAnyInsert() {
        register("mixed.case@test.ba", "password123");
        clearInvocations(userRepository);

        BusinessRuleException thrown = assertThrows(
                BusinessRuleException.class,
                () -> register("Mixed.Case@TEST.ba", "password123")
        );
        assertTrue(thrown.getMessage().toLowerCase().contains("already exists"),
                "Expected a duplicate-email message, got: " + thrown.getMessage());

        // Ovo je jedina tvrdnja koja razlikuje ispravku od buga. Bez normalizacije
        // prije provjere, existsByEmail gleda sirovi unos, ne nadje nista, kod produzi
        // i tek INSERT pukne na unique constraintu - a taj pad se hvata i pretvara u
        // ISTI BusinessRuleException, pa su izuzetak, poruka i broj redova identicni.
        // Razlikuje ih samo to da li je do upisa uopste doslo.
        verify(userRepository, never()).saveAndFlush(any(User.class));

        assertEquals(1, userRepository.count(), "Only one account may exist for that email");
    }

    @Test
    @DisplayName("Email is stored normalised, so login works in any case")
    void emailIsStoredNormalised() {
        register("  Normalise.Me@TEST.ba  ", "password123");

        assertTrue(userRepository.findByEmail("normalise.me@test.ba").isPresent(),
                "Email should have been trimmed and lowercased on the way in");

        assertDoesNotThrow(() ->
                authService.login(new LoginRequest("NORMALISE.ME@test.ba", "password123")));
    }

    // ---------- JWT filter ----------

    @Test
    @DisplayName("Protected endpoint without an Authorization header is 401")
    void requestWithoutTokenIsUnauthorised() throws Exception {
        mockMvc.perform(get(PROTECTED_PROBE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint with a garbage token is 401")
    void requestWithGarbageTokenIsUnauthorised() throws Exception {
        mockMvc.perform(get(PROTECTED_PROBE).header("Authorization", "Bearer not-a-jwt-at-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint with an expired token is 401")
    void requestWithExpiredTokenIsUnauthorised() throws Exception {
        AuthResponse registered = register("expired.token@test.ba", "password123");

        // Negativan vijek trajanja -> token kojem je istek vec prosao.
        JwtService expiredTokenIssuer = new JwtService(jwtSecret, -1L);
        String expired = expiredTokenIssuer.generateToken(principalOf(registered.userId()));

        mockMvc.perform(get(PROTECTED_PROBE).header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint with a valid token passes the filter")
    void requestWithValidTokenIsAuthenticated() throws Exception {
        AuthResponse registered = register("valid.token@test.ba", "password123");

        // 404, a ne 401: filter je autentikovao zahtjev, samo nema kontrolera na toj putanji.
        mockMvc.perform(get(PROTECTED_PROBE)
                        .header("Authorization", "Bearer " + registered.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Lowercase 'bearer' prefix is accepted (RFC 7235)")
    void lowercaseBearerPrefixIsAccepted() throws Exception {
        AuthResponse registered = register("lowercase.bearer@test.ba", "password123");

        mockMvc.perform(get(PROTECTED_PROBE)
                        .header("Authorization", "bearer " + registered.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A token issued before suspension stops working immediately")
    void validTokenOfSuspendedUserIsRejected() throws Exception {
        AuthResponse registered = register("suspended.later@test.ba", "password123");
        String token = registered.token();

        // Token i dalje ispravno potpisan i neistekao - mijenja se samo status naloga.
        mockMvc.perform(get(PROTECTED_PROBE).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        setAccountStatus(registered.userId(), AccountStatus.SUSPENDED);

        mockMvc.perform(get(PROTECTED_PROBE).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A token issued before deactivation stops working immediately")
    void validTokenOfDeactivatedUserIsRejected() throws Exception {
        AuthResponse registered = register("deactivated.later@test.ba", "password123");

        setAccountStatus(registered.userId(), AccountStatus.DEACTIVATED);

        mockMvc.perform(get(PROTECTED_PROBE)
                        .header("Authorization", "Bearer " + registered.token()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private AuthResponse register(String email, String password) {
        return authService.register(
                new RegisterRequest(email, password, "Test", "User", null));
    }

    private void setAccountStatus(java.util.UUID userId, AccountStatus status) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setAccountStatus(status);
        userRepository.saveAndFlush(user);
    }

    private UserPrincipal principalOf(java.util.UUID userId) {
        return UserPrincipal.withoutCredentials(userRepository.findById(userId).orElseThrow());
    }
}
