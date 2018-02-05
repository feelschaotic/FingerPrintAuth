package com.feelschaotic.utils;


public class FingerPrintException extends RuntimeException {
    public FingerPrintException(String message) {
        super(message);
    }

    public FingerPrintException(Throwable e) {
        super(e);
    }
}
