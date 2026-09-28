package com.aryan.ids.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import com.aryan.ids.TestFlows;
import org.junit.jupiter.api.Test;

class FeatureHasherTest {

    private final FeatureHasher hasher = new FeatureHasher();

    @Test
    void sameFeaturesInAnyOrderGiveSameHash() {
        Map<String, Double> reversed = new LinkedHashMap<>();
        reversed.put("SYN Flag Count", 1.0);
        reversed.put("Flow Duration", 20.0);
        reversed.put("Destination Port", 22.0);

        assertThat(hasher.hash(TestFlows.features(22, 20, 1))).isEqualTo(hasher.hash(reversed));
    }

    @Test
    void differentValuesGiveDifferentHash() {
        assertThat(hasher.hash(TestFlows.features(22, 20, 1)))
                .isNotEqualTo(hasher.hash(TestFlows.features(22, 21, 1)));
    }

    @Test
    void hashIsSha256Hex() {
        assertThat(hasher.hash(TestFlows.features(1, 2, 3))).matches("[0-9a-f]{64}");
    }
}
