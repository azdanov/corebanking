package dev.azdanov.corebanking.shared.exception;

public class InfrastructureException extends ApplicationException {

    public InfrastructureException(String message, Exception e) {
        super(message, e);
    }
}
