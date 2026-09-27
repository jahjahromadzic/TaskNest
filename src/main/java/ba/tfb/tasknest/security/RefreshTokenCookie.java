package ba.tfb.tasknest.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshTokenCookie {

    public static final String NAME = "refresh_token";
    public static final String PATH = "/api/auth";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshTokenCookie(@Value("${app.refresh-token.cookie-secure:true}") boolean secure,
                              @Value("${app.refresh-token.expiration-days}") long expirationDays) {
        this.secure = secure;
        this.maxAge = Duration.ofDays(expirationDays);
    }

    public ResponseCookie issue(String refreshToken) {
        return base(refreshToken).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
