package ba.tfb.tasknest.exception;

public class NotResourceOwnerException extends RuntimeException {
    public NotResourceOwnerException(String message) {
        super(message);
    }
}
