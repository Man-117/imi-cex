package org.william.cex.exception;

public class RiskLimitExceededException extends RuntimeException {
    public RiskLimitExceededException(String message) {
        super(message);
    }
}
