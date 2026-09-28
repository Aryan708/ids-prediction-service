package com.aryan.ids.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

/** Deterministic SHA-256 of a feature map (sorted by name), used as the cache key. */
@Component
public class FeatureHasher {

    public String hash(Map<String, Double> features) {
        StringBuilder canonical = new StringBuilder();
        new TreeMap<>(features).forEach((k, v) -> canonical.append(k).append('=').append(v).append(';'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
