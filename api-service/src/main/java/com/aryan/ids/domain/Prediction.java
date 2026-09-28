package com.aryan.ids.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** One scored network flow. Schema is owned by Flyway (db/migration). */
@Entity
@Table(name = "predictions")
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feature_hash", nullable = false, length = 64)
    private String featureHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Label label;

    @Column(name = "attack_probability", nullable = false)
    private double attackProbability;

    @Column(name = "model_version", nullable = false, length = 64)
    private String modelVersion;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "destination_port")
    private Integer destinationPort;

    @Column(name = "cache_hit", nullable = false)
    private boolean cacheHit;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Prediction() {
        // for JPA
    }

    public Prediction(String featureHash, Label label, double attackProbability, String modelVersion,
                      String sourceIp, Integer destinationPort, boolean cacheHit, long latencyMs) {
        this.featureHash = featureHash;
        this.label = label;
        this.attackProbability = attackProbability;
        this.modelVersion = modelVersion;
        this.sourceIp = sourceIp;
        this.destinationPort = destinationPort;
        this.cacheHit = cacheHit;
        this.latencyMs = latencyMs;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public String getFeatureHash() { return featureHash; }
    public Label getLabel() { return label; }
    public double getAttackProbability() { return attackProbability; }
    public String getModelVersion() { return modelVersion; }
    public String getSourceIp() { return sourceIp; }
    public Integer getDestinationPort() { return destinationPort; }
    public boolean isCacheHit() { return cacheHit; }
    public long getLatencyMs() { return latencyMs; }
    public Instant getCreatedAt() { return createdAt; }
}
