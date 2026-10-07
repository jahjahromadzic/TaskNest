package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.account.AccountResponse;
import ba.tfb.tasknest.dto.account.ChangePasswordRequest;
import ba.tfb.tasknest.dto.account.UpdateAccountRequest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.exception.IncorrectPasswordException;
import ba.tfb.tasknest.security.AuthThrottle;
import ba.tfb.tasknest.security.RefreshTokenCookie;
import ba.tfb.tasknest.security.UserPrincipal;
import ba.tfb.tasknest.service.AccountService;
import ba.tfb.tasknest.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AuthService authService;
    private final AuthThrottle authThrottle;
    private final RefreshTokenCookie refreshTokenCookie;

    @GetMapping
    public AccountResponse get(@AuthenticationPrincipal UserPrincipal principal) {
        return accountService.getAccount(principal.getId());
    }

    @PutMapping
    public AccountResponse update(@AuthenticationPrincipal UserPrincipal principal,
                                  @Valid @RequestBody UpdateAccountRequest request) {
        return accountService.updateAccount(principal.getId(), request);
    }

    @PostMapping("/password")
    public ResponseEntity<AuthResponse> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                                       @Valid @RequestBody ChangePasswordRequest request,
                                                       HttpServletRequest http) {
        String email = principal.getUsername();
        String address = http.getRemoteAddr();
        authThrottle.ensureLoginAllowed(email, address);

        AuthResponse response;
        try {
            response = authService.changePassword(principal.getId(), request);
        } catch (IncorrectPasswordException e) {
            authThrottle.recordFailedLogin(email, address);
            throw e;
        }

        authThrottle.recordSuccessfulLogin(email);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.issue(response.refreshToken()).toString())
                .body(response);
    }
}
