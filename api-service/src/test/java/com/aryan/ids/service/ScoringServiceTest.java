package com.aryan.ids.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.aryan.ids.TestFlows;
import com.aryan.ids.client.MlModels;
import com.aryan.ids.client.MlServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

class ScoringServiceTest {

    private MlServiceClient client;
    private ScoringService scoring;

    @BeforeEach
    void setUp() {
        client = mock(MlServiceClient.class);
        scoring = new ScoringService(client, new ConcurrentMapCacheManager());
    }

    @Test
    void secondIdenticalFlowIsServedFromCache() {
        Map<String, Double> flow = TestFlows.features(22, 5, 1);
        when(client.predict(anyList())).thenReturn(new MlModels.PredictResponse(TestFlows.MODEL_VERSION,
                List.of(new MlModels.Prediction("ATTACK", 0.97))));

        ScoringService.Result first = scoring.score(TestFlows.MODEL_VERSION, List.of(flow), List.of("h1")).get(0);
        ScoringService.Result second = scoring.score(TestFlows.MODEL_VERSION, List.of(flow), List.of("h1")).get(0);

        assertThat(first.cacheHit()).isFalse();
        assertThat(second.cacheHit()).isTrue();
        assertThat(second.score()).isEqualTo(first.score());
        verify(client, times(1)).predict(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void onlyCacheMissesAreSentToModelInOneCall() {
        Map<String, Double> a = TestFlows.features(80, 1000, 0);
        Map<String, Double> b = TestFlows.features(22, 5, 1);
        when(client.predict(anyList()))
                .thenReturn(new MlModels.PredictResponse(TestFlows.MODEL_VERSION,
                        List.of(new MlModels.Prediction("NORMAL", 0.02))))
                .thenReturn(new MlModels.PredictResponse(TestFlows.MODEL_VERSION,
                        List.of(new MlModels.Prediction("ATTACK", 0.91))));

        scoring.score(TestFlows.MODEL_VERSION, List.of(a), List.of("ha"));
        List<ScoringService.Result> results =
                scoring.score(TestFlows.MODEL_VERSION, List.of(a, b), List.of("ha", "hb"));

        ArgumentCaptor<List<Map<String, Double>>> sent = ArgumentCaptor.forClass(List.class);
        verify(client, times(2)).predict(sent.capture());
        assertThat(sent.getAllValues().get(1)).containsExactly(b);
        assertThat(results).extracting(ScoringService.Result::cacheHit).containsExactly(true, false);
        assertThat(results.get(1).score().label()).isEqualTo("ATTACK");
    }
}
