package com.aryan.ids.dto;

import java.util.List;

public record ModelInfoResponse(String modelVersion, double threshold, List<String> featureNames) {
}
