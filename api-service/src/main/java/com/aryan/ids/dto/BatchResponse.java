package com.aryan.ids.dto;

import java.util.List;

public record BatchResponse(int total, long attacks, List<PredictionResponse> predictions) {
}
