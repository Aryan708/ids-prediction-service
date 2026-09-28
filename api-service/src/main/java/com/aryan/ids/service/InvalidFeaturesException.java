package com.aryan.ids.service;

import java.util.List;

public class InvalidFeaturesException extends RuntimeException {

    private final List<String> errors;

    public InvalidFeaturesException(List<String> errors) {
        super("Invalid flow features");
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
