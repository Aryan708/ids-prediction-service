package com.aryan.ids.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.aryan.ids.client.MlModels;
import com.aryan.ids.client.MlServiceClient;
import com.aryan.ids.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * Scores flows through a read-through cache: cached flows are answered from Redis,
 * and all cache misses go to the model service in ONE batched HTTP call.
 */
@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private final MlServiceClient client;
    private final CacheManager cacheManager;

    public ScoringService(MlServiceClient client, CacheManager cacheManager) {
        this.client = client;
        this.cacheManager = cacheManager;
    }

    public record Result(Score score, boolean cacheHit) {
    }

    public List<Result> score(String modelVersion, List<Map<String, Double>> features, List<String> hashes) {
        Cache cache = cacheManager.getCache(CacheConfig.SCORES);
        Result[] results = new Result[features.size()];
        List<Integer> misses = new ArrayList<>();

        for (int i = 0; i < features.size(); i++) {
            Score hit = cacheGet(cache, key(modelVersion, hashes.get(i)));
            if (hit != null) {
                results[i] = new Result(hit, true);
            } else {
                misses.add(i);
            }
        }

        if (!misses.isEmpty()) {
            List<Map<String, Double>> toScore = misses.stream().map(features::get).toList();
            MlModels.PredictResponse response = client.predict(toScore);
            for (int j = 0; j < misses.size(); j++) {
                MlModels.Prediction p = response.predictions().get(j);
                Score score = new Score(p.label(), p.attackProbability(), response.modelVersion());
                int i = misses.get(j);
                results[i] = new Result(score, false);
                cachePut(cache, key(response.modelVersion(), hashes.get(i)), score);
            }
        }
        return Arrays.asList(results);
    }

    private static String key(String modelVersion, String hash) {
        return modelVersion + ":" + hash;
    }

    // Cache failures (e.g. Redis down) must never fail a prediction.
    private Score cacheGet(Cache cache, String key) {
        if (cache == null) {
            return null;
        }
        try {
            return cache.get(key, Score.class);
        } catch (RuntimeException e) {
            log.warn("Score cache read failed, scoring without cache: {}", e.getMessage());
            return null;
        }
    }

    private void cachePut(Cache cache, String key, Score score) {
        if (cache == null) {
            return;
        }
        try {
            cache.put(key, score);
        } catch (RuntimeException e) {
            log.warn("Score cache write failed: {}", e.getMessage());
        }
    }
}
