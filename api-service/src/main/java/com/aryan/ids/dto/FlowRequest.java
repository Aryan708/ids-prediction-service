package com.aryan.ids.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "One network flow to score")
public record FlowRequest(
        @Schema(description = "Optional source IP, stored with the prediction", example = "192.168.10.5")
        @Size(max = 45)
        String sourceIp,

        @Schema(description = "All 78 CICIDS2017 flow features, keyed by name (see GET /api/v1/model)")
        @NotEmpty
        Map<@NotBlank String, @NotNull Double> features) {
}
