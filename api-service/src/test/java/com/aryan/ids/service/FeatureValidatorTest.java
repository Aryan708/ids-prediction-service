package com.aryan.ids.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aryan.ids.TestFlows;
import org.junit.jupiter.api.Test;

class FeatureValidatorTest {

    private final FeatureValidator validator = new FeatureValidator();

    @Test
    void acceptsCompleteFiniteFeatureSet() {
        assertThat(validator.validate(TestFlows.features(80, 1000, 0), TestFlows.FEATURES)).isEmpty();
    }

    @Test
    void reportsMissingFeatures() {
        Map<String, Double> f = new HashMap<>(TestFlows.features(80, 1000, 0));
        f.remove("Flow Duration");

        List<String> errors = validator.validate(f, TestFlows.FEATURES);

        assertThat(errors).singleElement().asString().contains("missing 1").contains("Flow Duration");
    }

    @Test
    void reportsUnknownFeatures() {
        Map<String, Double> f = new HashMap<>(TestFlows.features(80, 1000, 0));
        f.put("Bogus Feature", 1.0);

        assertThat(validator.validate(f, TestFlows.FEATURES))
                .singleElement().asString().contains("unknown").contains("Bogus Feature");
    }

    @Test
    void reportsNonFiniteValues() {
        Map<String, Double> f = new HashMap<>(TestFlows.features(80, 1000, 0));
        f.put("Flow Duration", Double.POSITIVE_INFINITY);

        assertThat(validator.validate(f, TestFlows.FEATURES))
                .singleElement().asString().contains("non-finite").contains("Flow Duration");
    }
}
