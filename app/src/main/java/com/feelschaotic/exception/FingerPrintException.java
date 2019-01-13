package com.feelschaotic.exception;


public class FingerPrintException extends RuntimeException {
    public FingerPrintException(String message) {
        super(message);
    }

    public FingerPrintException(Throwable e) {
        super(e);
    }
}
