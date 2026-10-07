package ba.tfb.tasknest.service;

import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.VerificationToken;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.entity.enums.VerificationTokenType;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.messaging.PasswordResetMailer;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.repository.VerificationTokenRepository;
import ba.tfb.tasknest.security.AuthThrottle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

@Service
@Slf4j
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final String INVALID_LINK = "This reset link is invalid or has expired. Ask for a new one.";

    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;
    private final AuthThrottle throttle;
    private final Clock clock;
    private final long validMinutes;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserRepository userRepository,
                                VerificationTokenRepository tokenRepository,
                                RefreshTokenService refreshTokenService,
                                PasswordEncoder passwordEncoder,
                                PasswordResetMailer mailer,
                                AuthThrottle throttle,
                                Clock clock,
                                @Value("${app.password-reset.expiration-minutes}") long validMinutes) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.throttle = throttle;
        this.clock = clock;
        this.validMinutes = validMinutes;
    }

    @Transactional
    public void requestReset(String email, String address) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!throttle.allowResetRequest(normalized, address)) {
            return;
        }

        User user = userRepository.findByEmail(normalized)
                .filter(found -> found.getAccountStatus() == AccountStatus.ACTIVE)
                .orElse(null);
        if (user == null) {
            log.info("Password reset requested for an unknown or inactive account");
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        tokenRepository.markUnusedAsUsed(user.getId(), VerificationTokenType.PASSWORD_RESET, now);

        String value = newTokenValue();
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setType(VerificationTokenType.PASSWORD_RESET);
        token.setToken(RefreshTokenService.hash(value));
        token.setExpiresAt(now.plusMinutes(validMinutes));
        tokenRepository.save(token);

        mailer.sendResetLink(user.getEmail(), user.getFirstName(), value, validMinutes);
    }

    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        LocalDateTime now = LocalDateTime.now(clock);
        VerificationToken token = tokenRepository
                .findByTokenAndType(RefreshTokenService.hash(tokenValue), VerificationTokenType.PASSWORD_RESET)
                .filter(found -> found.getUsedAt() == null && found.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new BusinessRuleException(INVALID_LINK));

        User user = token.getUser();
        token.setUsedAt(now);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokenService.endAllSessions(user.getId());
        throttle.recordSuccessfulLogin(user.getEmail());
    }

    private String newTokenValue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
