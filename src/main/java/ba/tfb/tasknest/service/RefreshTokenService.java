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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

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
    public IssuedRefreshToken issue(User user) {
        String value = generateTokenValue();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(value));
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(expirationDays));
        refreshTokenRepository.save(refreshToken);

        return new IssuedRefreshToken(user, value);
    }

    public static String hash(String tokenValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(tokenValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    @Transactional
    public IssuedRefreshToken validateAndRotate(String tokenValue) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(tokenValue))
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
        refreshTokenRepository.findByTokenHash(hash(tokenValue)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional
    public void endAllSessions(java.util.UUID userId) {
        refreshTokenRepository.deleteAllByUser(userId);
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
