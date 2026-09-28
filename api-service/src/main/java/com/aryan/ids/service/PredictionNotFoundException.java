package com.aryan.ids.service;

public class PredictionNotFoundException extends RuntimeException {

    public PredictionNotFoundException(Long id) {
        super("Prediction " + id + " not found");
    }
}
