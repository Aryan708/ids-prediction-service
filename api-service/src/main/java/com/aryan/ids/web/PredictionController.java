package com.aryan.ids.web;

import java.net.URI;

import com.aryan.ids.domain.Label;
import com.aryan.ids.dto.BatchRequest;
import com.aryan.ids.dto.BatchResponse;
import com.aryan.ids.dto.FlowRequest;
import com.aryan.ids.dto.ModelInfoResponse;
import com.aryan.ids.dto.PagedResponse;
import com.aryan.ids.dto.PredictionResponse;
import com.aryan.ids.dto.StatsResponse;
import com.aryan.ids.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Predictions", description = "Score network flows and browse prediction history")
public class PredictionController {

    private final PredictionService service;

    public PredictionController(PredictionService service) {
        this.service = service;
    }

    @Operation(summary = "Score one network flow")
    @PostMapping("/predictions")
    public ResponseEntity<PredictionResponse> predict(@Valid @RequestBody FlowRequest request) {
        PredictionResponse response = service.predict(request);
        return ResponseEntity.created(URI.create("/api/v1/predictions/" + response.id())).body(response);
    }

    @Operation(summary = "Score up to 500 flows in one call")
    @PostMapping("/predictions/batch")
    public BatchResponse predictBatch(@Valid @RequestBody BatchRequest request) {
        return service.predictBatch(request.flows());
    }

    @Operation(summary = "Get one stored prediction")
    @GetMapping("/predictions/{id}")
    public PredictionResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "List predictions, newest first, optionally filtered by label")
    @GetMapping("/predictions")
    public PagedResponse<PredictionResponse> list(@RequestParam(required = false) Label label,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.list(label, page, size);
    }

    @Operation(summary = "Aggregate stats (cached for 30s)")
    @GetMapping("/predictions/stats")
    public StatsResponse stats() {
        return service.stats();
    }

    @Operation(summary = "Model version, threshold and the 78 expected feature names")
    @GetMapping("/model")
    public ModelInfoResponse model() {
        return service.modelInfo();
    }
}
