package ba.tfb.tasknest.exception;

public class IncorrectPasswordException extends BusinessRuleException {
    public IncorrectPasswordException(String message) {
        super(message);
    }
}
