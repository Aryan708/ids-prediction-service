package com.aryan.ids.dto;

public record StatsResponse(
        long totalPredictions,
        long attacks,
        long normal,
        double attackRate,
        Double averageLatencyMs) {
}
