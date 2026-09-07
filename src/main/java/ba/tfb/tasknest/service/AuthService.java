package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.LoginRequest;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.auth.TaskerActivationResponse;
import ba.tfb.tasknest.entity.RefreshToken;
import ba.tfb.tasknest.entity.Role;
import ba.tfb.tasknest.entity.TaskerProfile;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.entity.enums.RoleName;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.repository.RoleRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.security.JwtService;
import ba.tfb.tasknest.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserDetailsChecker accountStatusChecker;
    private final RefreshTokenService refreshTokenService;
    private final TaskerProfileRepository taskerProfileRepository;

    /**
     * Registers a new account. Every account starts as a client;
     * the tasker role is activated later from within the app.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Normalizacija JEDNOM, prije provjere: ranije se provjeravao sirovi unos
        // a upisivala mala slova, pa je "Foo@Test.ba" prolazio provjeru i pucao
        // na unique constraintu.
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleException("An account with this email already exists");
        }

        Role clientRole = roleRepository.findByName(RoleName.CLIENT)
                .orElseThrow(() -> new IllegalStateException("CLIENT role is missing"));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.getRoles().add(clientRole);

        try {
            // saveAndFlush, ne save: provjera iznad je check-then-act i ne stiti od
            // paralelnih registracija istog emaila. Unique constraint je stvarna
            // zastita, a bez flusha bi pukao tek na commitu, izvan ovog catch-a.
            User saved = userRepository.saveAndFlush(user);
            return buildResponse(UserPrincipal.withCredentials(saved), saved);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("An account with this email already exists");
        }
    }

    // Vise nije readOnly: login sada upisuje refresh token, a u read-only
    // transakciji Hibernate radi u FlushMode.MANUAL pa bi insert tiho izostao.
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        UserPrincipal principal = UserPrincipal.withCredentials(user);

        // Namjerno TEK nakon provjere lozinke: inace bi neko bez lozinke saznao
        // da nalog postoji i da je suspendovan. Baca LockedException / DisabledException.
        accountStatusChecker.check(principal);

        return buildResponse(principal, user);
    }

    /**
     * Mijenja refresh token za novi par tokena. Stari se pri tome opoziva
     * (rotacija) - vidi {@link RefreshTokenService#validateAndRotate(String)}.
     */
    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        RefreshToken rotated = refreshTokenService.validateAndRotate(refreshTokenValue);
        User user = rotated.getUser();

        UserPrincipal principal = UserPrincipal.withCredentials(user);

        // Status se provjerava i ovdje, ne samo pri loginu: refresh token zivi
        // danima, pa suspenzija u medjuvremenu mora sprijeciti novi access token.
        accountStatusChecker.check(principal);

        return buildResponse(principal, user, rotated.getToken());
    }

    /** Opoziv umjesto brisanja - red ostaje kao audit trag. */
    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenService.revoke(refreshTokenValue);
    }

    /** Locale.ROOT, ne podrazumijevani - u turskom "I" ne prelazi u "i". */
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse buildResponse(UserPrincipal principal, User user) {
        return buildResponse(principal, user, refreshTokenService.issue(user).getToken());
    }

    private AuthResponse buildResponse(UserPrincipal principal, User user, String refreshToken) {
        return new AuthResponse(
                jwtService.generateToken(principal),
                refreshToken,
                jwtService.getAccessTokenExpirySeconds(),
                user.getId(),
                user.getEmail(),
                user.getFirstName() + " " + user.getLastName(),
                user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .toList()
        );
    }

    @Transactional
    public TaskerActivationResponse activateTaskerRole(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        boolean alreadyTasker = user.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.TASKER);

        if (alreadyTasker) {
            throw new BusinessRuleException("Tasker role is already active on this account");
        }

        Role taskerRole = roleRepository.findByName(RoleName.TASKER)
                .orElseThrow(() -> new IllegalStateException("TASKER role is missing"));

        user.getRoles().add(taskerRole);

        TaskerProfile profile = new TaskerProfile();
        profile.setUser(user);
        profile.setCompletedJobsCount(0);
        profile.setVerified(false);
        taskerProfileRepository.save(profile);

        // Namjerno bez novog para tokena: role se citaju iz baze na svaki zahtjev,
        // pa postojeci access token vec od sljedeceg poziva nosi i TASKER rolu.
        // Izdavanje novog refresh tokena bi ovdje samo gomilalo redove.
        return new TaskerActivationResponse(
                user.getId(),
                profile.getId(),
                user.getRoles().stream().map(role -> role.getName().name()).toList()
        );
    }
}
