package org.william.cex.api.exception;

public class RiskLimitExceededException extends RuntimeException {
    public RiskLimitExceededException(String message) {
        super(message);
    }
}
