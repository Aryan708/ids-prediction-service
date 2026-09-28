package com.aryan.ids.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/** Checks a flow has exactly the features the model expects, all finite numbers. */
@Component
public class FeatureValidator {

    private static final int MAX_LISTED = 5;

    public List<String> validate(Map<String, Double> features, List<String> expected) {
        List<String> errors = new ArrayList<>();

        List<String> missing = expected.stream().filter(name -> !features.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            errors.add("missing " + missing.size() + " feature(s): " + preview(missing));
        }

        Set<String> known = new HashSet<>(expected);
        List<String> unknown = features.keySet().stream().filter(name -> !known.contains(name)).sorted().toList();
        if (!unknown.isEmpty()) {
            errors.add("unknown feature(s): " + preview(unknown));
        }

        List<String> nonFinite = features.entrySet().stream()
                .filter(e -> e.getValue() == null || !Double.isFinite(e.getValue()))
                .map(Map.Entry::getKey).sorted().toList();
        if (!nonFinite.isEmpty()) {
            errors.add("non-finite value(s) for: " + preview(nonFinite));
        }
        return errors;
    }

    private static String preview(List<String> names) {
        return names.size() <= MAX_LISTED
                ? String.join(", ", names)
                : String.join(", ", names.subList(0, MAX_LISTED)) + " ...";
    }
}
