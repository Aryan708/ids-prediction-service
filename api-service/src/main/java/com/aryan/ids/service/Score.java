package com.aryan.ids.service;

/** Model output for one flow. This is what gets cached in Redis. */
public record Score(String label, double attackProbability, String modelVersion) {
}
