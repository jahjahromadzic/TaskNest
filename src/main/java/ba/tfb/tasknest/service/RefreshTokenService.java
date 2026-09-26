package ba.tfb.tasknest.service;

import ba.tfb.tasknest.entity.RefreshToken;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import ba.tfb.tasknest.exception.InvalidRefreshTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@Slf4j
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final long expirationDays;

    private final TransactionTemplate inNewTransaction;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               PlatformTransactionManager transactionManager,
                               @Value("${app.refresh-token.expiration-days}") long expirationDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.expirationDays = expirationDays;
        this.inNewTransaction = new TransactionTemplate(transactionManager);
        this.inNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public RefreshToken issue(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(generateTokenValue());
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(expirationDays));

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public RefreshToken validateAndRotate(String tokenValue) {
        RefreshToken existing = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));

        if (existing.getRevokedAt() != null) {
            handleReuse(existing);
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        existing.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.saveAndFlush(existing);

        return issue(existing.getUser());
    }

    @Transactional
    public void revoke(String tokenValue) {
        refreshTokenRepository.findByToken(tokenValue).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    private void handleReuse(RefreshToken reused) {
        java.util.UUID userId = reused.getUser().getId();

        log.warn("Reuse of a revoked refresh token for user {}; "
                + "revoking all tokens of that user", userId);

        inNewTransaction.executeWithoutResult(status ->
                refreshTokenRepository.revokeAllByUser(userId, LocalDateTime.now()));

        throw new InvalidRefreshTokenException("Refresh token has already been used");
    }

    private String generateTokenValue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }
}
