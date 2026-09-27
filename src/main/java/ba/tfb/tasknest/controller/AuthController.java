package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.LoginRequest;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.auth.TaskerActivationResponse;
import ba.tfb.tasknest.exception.InvalidRefreshTokenException;
import ba.tfb.tasknest.security.RefreshTokenCookie;
import ba.tfb.tasknest.security.UserPrincipal;
import ba.tfb.tasknest.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withRefreshCookie(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(HttpStatus.OK, authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token is missing");
        }
        return withRefreshCookie(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear().toString())
                .build();
    }

    @PostMapping("/activate-tasker")
    public TaskerActivationResponse activateTasker(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.activateTaskerRole(principal.getId());
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthResponse auth) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.issue(auth.refreshToken()).toString())
                .body(auth);
    }
}
