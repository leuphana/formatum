package de.leuphana.escience.formatum.exception;

public class PandocConversionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PandocConversionException(String message) {
        super(message);
    }

    public PandocConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}