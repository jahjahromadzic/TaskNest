package ba.tfb.tasknest.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the bearer token on every request and, when valid,
 * puts the authenticated user into the security context.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserDetailsChecker accountStatusChecker;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);

        if (header == null || !hasBearerPrefix(header)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length()).trim();

        jwtService.parseAccessToken(token)
                .flatMap(jwtService::extractUserId)
                .flatMap(userDetailsService::loadUserById)
                .ifPresent(principal -> authenticate(principal, request));

        filterChain.doFilter(request, response);
    }

    private void authenticate(UserPrincipal principal, HttpServletRequest request) {
        try {
            // Status se provjerava na svakom zahtjevu, ne samo pri loginu: token
            // vrijedi do sat vremena, a suspenzija mora djelovati odmah.
            accountStatusChecker.check(principal);
        } catch (AccountStatusException e) {
            log.debug("Token odbijen za korisnika {}: {}", principal.getId(), e.getMessage());
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** RFC 7235: shema je case-insensitive, pa "bearer" i "BEARER" moraju proci. */
    private static boolean hasBearerPrefix(String header) {
        return header.length() > PREFIX.length()
                && header.regionMatches(true, 0, PREFIX, 0, PREFIX.length());
    }
}
