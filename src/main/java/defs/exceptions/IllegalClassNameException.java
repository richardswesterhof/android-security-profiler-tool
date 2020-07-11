package defs.exceptions;

public class IllegalClassNameException extends RuntimeException {

    public IllegalClassNameException() {
        super();
    }

    public IllegalClassNameException(String message) {
        super(message);
    }

    public IllegalClassNameException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalClassNameException(Throwable cause) {
        super(cause);
    }
}
