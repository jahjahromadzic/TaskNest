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

/**
 * Izdavanje, provjera i opoziv refresh tokena.
 * <p>
 * Refresh token namjerno NIJE JWT: svaki poziv ga ionako trazi u bazi, pa
 * potpisani token ne bi dao nista osim vece duzine. Nasumican string je uz to
 * trivijalno opozvati, sto je kod JWT-a bez dodatne liste nemoguce.
 */
@Service
@Slf4j
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final long expirationDays;

    /**
     * Zasebna transakcija za opoziv pri detekciji ponovne upotrebe. Bez nje bi
     * se opoziv rollbackovao zajedno s izuzetkom koji odbija zahtjev, pa mjera
     * ne bi imala nikakav efekat.
     */
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

    /**
     * Provjerava token i odmah ga opoziva - ROTACIJA. Svaka upotreba trosi token
     * i vraca novi.
     * <p>
     * Rotacija postoji zato sto refresh token zivi danima i putuje na svaki
     * poziv osvjezavanja. Bez nje, jednom ukraden token vrijedi do isteka i
     * krada se ne moze primijetiti. S rotacijom svaki token vrijedi jednom, pa
     * ponovna upotreba starog tokena postaje mjerljiv signal - vidi
     * {@link #handleReuse(RefreshToken)}.
     */
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

    /**
     * Vec opozvan token stigao je ponovo. Dva scenarija se spolja ne razlikuju:
     * klijent je izgubio odgovor na prethodni refresh i ponavlja poziv, ili je
     * neko ukrao token koji je pravi vlasnik u medjuvremenu vec zamijenio.
     * <p>
     * Opozivaju se SVI tokeni korisnika. Cijena greske je nesimetricna: ako je
     * bio klijentski retry, korisnik se mora ponovo prijaviti; ako je bila
     * krada, a ne uradimo nista, napadac zadrzava pristup do isteka. Ovo je i
     * preporuka OAuth 2.0 Security BCP-a za rotirajuce refresh tokene.
     */
    private void handleReuse(RefreshToken reused) {
        java.util.UUID userId = reused.getUser().getId();

        log.warn("Ponovna upotreba opozvanog refresh tokena za korisnika {} - "
                + "opozivam sve tokene tog korisnika", userId);

        // REQUIRES_NEW: opoziv se mora commitati nezavisno, jer izuzetak ispod
        // rusi tekucu transakciju i povukao bi opoziv sa sobom.
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
