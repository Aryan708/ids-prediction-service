package com.aryan.ids.client;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Wire format of the Python model service (snake_case JSON). */
public final class MlModels {

    private MlModels() {
    }

    public record PredictRequest(List<Map<String, Double>> instances) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Prediction(
            String label,
            @JsonProperty("attack_probability") double attackProbability) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PredictResponse(
            @JsonProperty("model_version") String modelVersion,
            List<Prediction> predictions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
            @JsonProperty("model_version") String modelVersion,
            @JsonProperty("feature_names") List<String> featureNames,
            double threshold) {
    }
}
