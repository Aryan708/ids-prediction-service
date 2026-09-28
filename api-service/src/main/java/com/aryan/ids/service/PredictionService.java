package com.aryan.ids.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.aryan.ids.client.MlModels;
import com.aryan.ids.config.CacheConfig;
import com.aryan.ids.domain.Label;
import com.aryan.ids.domain.Prediction;
import com.aryan.ids.dto.BatchResponse;
import com.aryan.ids.dto.FlowRequest;
import com.aryan.ids.dto.ModelInfoResponse;
import com.aryan.ids.dto.PagedResponse;
import com.aryan.ids.dto.PredictionResponse;
import com.aryan.ids.dto.StatsResponse;
import com.aryan.ids.repository.PredictionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class PredictionService {

    static final String DESTINATION_PORT = "Destination Port";

    private final ModelMetadataService metadataService;
    private final FeatureValidator validator;
    private final FeatureHasher hasher;
    private final ScoringService scoringService;
    private final PredictionRepository repository;

    public PredictionService(ModelMetadataService metadataService, FeatureValidator validator, FeatureHasher hasher,
                             ScoringService scoringService, PredictionRepository repository) {
        this.metadataService = metadataService;
        this.validator = validator;
        this.hasher = hasher;
        this.scoringService = scoringService;
        this.repository = repository;
    }

    public PredictionResponse predict(FlowRequest flow) {
        return predictAll(List.of(flow)).get(0);
    }

    public BatchResponse predictBatch(List<FlowRequest> flows) {
        List<PredictionResponse> results = predictAll(flows);
        long attacks = results.stream().filter(r -> r.label() == Label.ATTACK).count();
        return new BatchResponse(results.size(), attacks, results);
    }

    private List<PredictionResponse> predictAll(List<FlowRequest> flows) {
        long start = System.nanoTime();
        MlModels.Metadata model = metadataService.get();

        List<String> errors = new ArrayList<>();
        for (int i = 0; i < flows.size(); i++) {
            String prefix = flows.size() > 1 ? "flows[" + i + "]: " : "";
            validator.validate(flows.get(i).features(), model.featureNames())
                    .forEach(err -> errors.add(prefix + err));
        }
        if (!errors.isEmpty()) {
            throw new InvalidFeaturesException(errors);
        }

        List<Map<String, Double>> features = flows.stream().map(FlowRequest::features).toList();
        List<String> hashes = features.stream().map(hasher::hash).toList();
        List<ScoringService.Result> scored = scoringService.score(model.modelVersion(), features, hashes);
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        List<Prediction> entities = new ArrayList<>(flows.size());
        for (int i = 0; i < flows.size(); i++) {
            ScoringService.Result r = scored.get(i);
            entities.add(new Prediction(
                    hashes.get(i),
                    Label.from(r.score().label()),
                    r.score().attackProbability(),
                    r.score().modelVersion(),
                    flows.get(i).sourceIp(),
                    destinationPort(features.get(i)),
                    r.cacheHit(),
                    latencyMs));
        }
        return repository.saveAll(entities).stream().map(PredictionResponse::from).toList();
    }

    public PredictionResponse get(Long id) {
        return repository.findById(id)
                .map(PredictionResponse::from)
                .orElseThrow(() -> new PredictionNotFoundException(id));
    }

    public PagedResponse<PredictionResponse> list(Label label, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Prediction> result = label == null
                ? repository.findAll(pageable)
                : repository.findByLabel(label, pageable);
        return PagedResponse.of(result.map(PredictionResponse::from));
    }

    @Cacheable(CacheConfig.STATS)
    public StatsResponse stats() {
        long total = repository.count();
        long attacks = repository.countByLabel(Label.ATTACK);
        double rate = total == 0 ? 0.0 : (double) attacks / total;
        return new StatsResponse(total, attacks, total - attacks, rate, repository.averageLatencyMs());
    }

    public ModelInfoResponse modelInfo() {
        MlModels.Metadata m = metadataService.get();
        return new ModelInfoResponse(m.modelVersion(), m.threshold(), m.featureNames());
    }

    private static Integer destinationPort(Map<String, Double> features) {
        Double port = features.get(DESTINATION_PORT);
        return port == null ? null : port.intValue();
    }
}
