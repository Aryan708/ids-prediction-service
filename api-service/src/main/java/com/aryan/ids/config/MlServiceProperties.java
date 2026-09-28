package com.aryan.ids.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Connection settings for the Python model service (prefix {@code ml-service}). */
@Validated
@ConfigurationProperties(prefix = "ml-service")
public record MlServiceProperties(
        @NotBlank String baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {
}
