package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.BecomeTaskerRequest;
import ba.tfb.tasknest.dto.auth.LoginRequest;
import ba.tfb.tasknest.dto.auth.PasswordResetConfirmRequest;
import ba.tfb.tasknest.dto.auth.PasswordResetRequest;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.auth.TaskerActivationResponse;
import ba.tfb.tasknest.exception.InvalidRefreshTokenException;
import ba.tfb.tasknest.security.AuthThrottle;
import ba.tfb.tasknest.security.RefreshTokenCookie;
import ba.tfb.tasknest.security.UserPrincipal;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;
    private final PasswordResetService passwordResetService;
    private final AuthThrottle authThrottle;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withRefreshCookie(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String address = http.getRemoteAddr();
        authThrottle.ensureLoginAllowed(request.email(), address);

        AuthResponse response;
        try {
            response = authService.login(request);
        } catch (BadCredentialsException e) {
            authThrottle.recordFailedLogin(request.email(), address);
            throw e;
        }

        authThrottle.recordSuccessfulLogin(request.email());
        return withRefreshCookie(HttpStatus.OK, response);
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest http) {
        passwordResetService.requestReset(request.email(), http.getRemoteAddr());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.resetPassword(request.token(), request.password());
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
    public TaskerActivationResponse activateTasker(@AuthenticationPrincipal UserPrincipal principal,
                                                   @Valid @RequestBody BecomeTaskerRequest request) {
        return authService.becomeTasker(principal.getId(), request);
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthResponse auth) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.issue(auth.refreshToken()).toString())
                .body(auth);
    }
}
