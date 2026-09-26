package ba.tfb.tasknest.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class JwtService {

    static final String TYPE_CLAIM = "type";

    static final String ACCESS_TOKEN_TYPE = "access";

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.secret is not set. Set the JWT_SECRET environment variable; "
                            + "the application refuses to start with an empty signing key.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public long getAccessTokenExpirySeconds() {
        return expirationMinutes * 60;
    }

    public String generateToken(UserPrincipal principal) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(principal.getId().toString())
                .claim("email", principal.getEmail())
                .claim(TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public Optional<Claims> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String type = claims.get(TYPE_CLAIM, String.class);
            if (!ACCESS_TOKEN_TYPE.equals(type)) {
                log.debug("Token rejected: expected type '{}', got '{}'", ACCESS_TOKEN_TYPE, type);
                return Optional.empty();
            }

            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token rejected: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<UUID> extractUserId(Claims claims) {
        String subject = claims.getSubject();
        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException e) {
            log.debug("Token rejected: subject '{}' is not a UUID", subject);
            return Optional.empty();
        }
    }
}
