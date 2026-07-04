package dev.azdanov.corebanking.shared.exception;

public class InvalidInputException extends ApplicationException {

    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(String message, Exception e) {
        super(message, e);
    }
}
