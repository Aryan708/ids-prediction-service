package com.aryan.ids.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.aryan.ids.TestFlows;
import com.aryan.ids.domain.Label;
import com.aryan.ids.dto.BatchResponse;
import com.aryan.ids.dto.FlowRequest;
import com.aryan.ids.dto.PredictionResponse;
import com.aryan.ids.dto.StatsResponse;
import com.aryan.ids.repository.PredictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PredictionServiceTest {

    @Mock
    private ModelMetadataService metadataService;
    @Mock
    private ScoringService scoringService;
    @Mock
    private PredictionRepository repository;

    private PredictionService service;

    @BeforeEach
    void setUp() {
        service = new PredictionService(metadataService, new FeatureValidator(), new FeatureHasher(),
                scoringService, repository);
    }

    private void modelAvailable() {
        when(metadataService.get()).thenReturn(TestFlows.metadata());
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ScoringService.Result result(String label, double p, boolean cacheHit) {
        return new ScoringService.Result(new Score(label, p, TestFlows.MODEL_VERSION), cacheHit);
    }

    @Test
    void predictMapsModelScoreToStoredPrediction() {
        modelAvailable();
        when(scoringService.score(anyString(), anyList(), anyList())).thenReturn(List.of(result("ATTACK", 0.93, false)));

        PredictionResponse r = service.predict(new FlowRequest("10.0.0.7", TestFlows.features(22, 5, 1)));

        assertThat(r.label()).isEqualTo(Label.ATTACK);
        assertThat(r.attackProbability()).isEqualTo(0.93);
        assertThat(r.modelVersion()).isEqualTo(TestFlows.MODEL_VERSION);
        assertThat(r.destinationPort()).isEqualTo(22);
        assertThat(r.sourceIp()).isEqualTo("10.0.0.7");
        assertThat(r.cacheHit()).isFalse();
    }

    @Test
    void invalidFeaturesAreRejectedBeforeCallingTheModel() {
        when(metadataService.get()).thenReturn(TestFlows.metadata());
        Map<String, Double> incomplete = new HashMap<>(TestFlows.features(22, 5, 1));
        incomplete.remove("SYN Flag Count");

        assertThatThrownBy(() -> service.predict(new FlowRequest(null, incomplete)))
                .isInstanceOf(InvalidFeaturesException.class)
                .satisfies(e -> assertThat(((InvalidFeaturesException) e).getErrors())
                        .anyMatch(msg -> msg.contains("SYN Flag Count")));
        verify(scoringService, never()).score(anyString(), anyList(), anyList());
        verify(repository, never()).saveAll(any());
    }

    @Test
    void batchErrorsAreIndexedByFlow() {
        when(metadataService.get()).thenReturn(TestFlows.metadata());
        Map<String, Double> bad = new HashMap<>(TestFlows.features(22, 5, 1));
        bad.put("Extra", 1.0);

        assertThatThrownBy(() -> service.predictBatch(List.of(
                new FlowRequest(null, TestFlows.features(80, 100, 0)),
                new FlowRequest(null, bad))))
                .isInstanceOf(InvalidFeaturesException.class)
                .satisfies(e -> assertThat(((InvalidFeaturesException) e).getErrors())
                        .singleElement().asString().startsWith("flows[1]:"));
    }

    @Test
    void batchCountsAttacks() {
        modelAvailable();
        when(scoringService.score(anyString(), anyList(), anyList())).thenReturn(List.of(
                result("NORMAL", 0.01, false), result("ATTACK", 0.88, true), result("ATTACK", 0.99, false)));

        BatchResponse r = service.predictBatch(List.of(
                new FlowRequest(null, TestFlows.features(80, 1000, 0)),
                new FlowRequest(null, TestFlows.features(22, 5, 1)),
                new FlowRequest(null, TestFlows.features(23, 4, 1))));

        assertThat(r.total()).isEqualTo(3);
        assertThat(r.attacks()).isEqualTo(2);
        assertThat(r.predictions()).extracting(PredictionResponse::cacheHit).containsExactly(false, true, false);
    }

    @Test
    void getUnknownIdThrowsNotFound() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(42L)).isInstanceOf(PredictionNotFoundException.class);
    }

    @Test
    void statsComputesAttackRate() {
        when(repository.count()).thenReturn(8L);
        when(repository.countByLabel(Label.ATTACK)).thenReturn(2L);
        when(repository.averageLatencyMs()).thenReturn(12.5);

        StatsResponse s = service.stats();

        assertThat(s.normal()).isEqualTo(6);
        assertThat(s.attackRate()).isEqualTo(0.25);
        assertThat(s.averageLatencyMs()).isEqualTo(12.5);
    }

    @Test
    void statsWithNoDataHasZeroRate() {
        when(repository.count()).thenReturn(0L);

        assertThat(service.stats().attackRate()).isZero();
    }
}
