package de.leuphana.escience.formatum.exception;

public class MetadataValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MetadataValidationException(String message) {
        super(message);
    }
}