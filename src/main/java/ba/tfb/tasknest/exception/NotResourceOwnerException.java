package ba.tfb.tasknest.exception;

/**
 * Korisnik nije vlasnik resursa nad kojim pokusava raditi. Namjerno se ne zove
 * AccessDeniedException da se ne mijesa sa istoimenom klasom iz Spring Security-ja,
 * koju njegov ExceptionTranslationFilter hvata i pretvara u 403.
 */
public class NotResourceOwnerException extends RuntimeException {
    public NotResourceOwnerException(String message) {
        super(message);
    }
}
