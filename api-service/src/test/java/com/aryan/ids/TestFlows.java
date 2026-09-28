package com.aryan.ids;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aryan.ids.client.MlModels;

/** Small feature set used in tests instead of all 78 CICIDS2017 features. */
public final class TestFlows {

    public static final List<String> FEATURES = List.of("Destination Port", "Flow Duration", "SYN Flag Count");
    public static final String MODEL_VERSION = "test-model";

    private TestFlows() {
    }

    public static Map<String, Double> features(double port, double duration, double syn) {
        Map<String, Double> f = new LinkedHashMap<>();
        f.put("Destination Port", port);
        f.put("Flow Duration", duration);
        f.put("SYN Flag Count", syn);
        return f;
    }

    public static MlModels.Metadata metadata() {
        return new MlModels.Metadata(MODEL_VERSION, FEATURES, 0.5);
    }
}
