package com.aryan.ids.dto;

import java.time.Instant;

import com.aryan.ids.domain.Label;
import com.aryan.ids.domain.Prediction;

public record PredictionResponse(
        Long id,
        Label label,
        double attackProbability,
        String modelVersion,
        String sourceIp,
        Integer destinationPort,
        boolean cacheHit,
        long latencyMs,
        Instant createdAt) {

    public static PredictionResponse from(Prediction p) {
        return new PredictionResponse(p.getId(), p.getLabel(), p.getAttackProbability(), p.getModelVersion(),
                p.getSourceIp(), p.getDestinationPort(), p.isCacheHit(), p.getLatencyMs(), p.getCreatedAt());
    }
}
