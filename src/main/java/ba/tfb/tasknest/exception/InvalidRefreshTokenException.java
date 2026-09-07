package ba.tfb.tasknest.exception;

/**
 * Refresh token ne postoji, istekao je ili je vec iskoristen.
 * <p>
 * Namjerno nije BadCredentialsException: taj tip je rezervisan za pogresnu
 * kombinaciju emaila i lozinke, pa je handler mapirao i probleme s refresh
 * tokenom u poruku "Invalid email or password", sto klijentu nije govorilo nista.
 */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
