package dev.azdanov.corebanking.shared.exception;

public abstract class ApplicationException extends RuntimeException {

    protected ApplicationException(String message) {
        super(message);
    }

    protected ApplicationException(String message, Exception e) {
        super(message, e);
    }
}
