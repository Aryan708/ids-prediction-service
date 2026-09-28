package com.aryan.ids.service;

import java.time.Duration;
import java.time.Instant;

import com.aryan.ids.client.MlModels;
import com.aryan.ids.client.MlServiceClient;
import org.springframework.stereotype.Service;

/** Keeps the model's feature list / version in memory, refreshed every 5 minutes. */
@Service
public class ModelMetadataService {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final MlServiceClient client;
    private volatile MlModels.Metadata cached;
    private volatile Instant fetchedAt = Instant.EPOCH;

    public ModelMetadataService(MlServiceClient client) {
        this.client = client;
    }

    public MlModels.Metadata get() {
        if (isStale()) {
            synchronized (this) {
                if (isStale()) {
                    cached = client.metadata();
                    fetchedAt = Instant.now();
                }
            }
        }
        return cached;
    }

    private boolean isStale() {
        return cached == null || Instant.now().isAfter(fetchedAt.plus(TTL));
    }
}
