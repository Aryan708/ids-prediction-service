CREATE TABLE predictions (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    feature_hash       VARCHAR(64)  NOT NULL,
    label              VARCHAR(10)  NOT NULL,
    attack_probability DOUBLE       NOT NULL,
    model_version      VARCHAR(64)  NOT NULL,
    source_ip          VARCHAR(45),
    destination_port   INT,
    cache_hit          BOOLEAN      NOT NULL,
    latency_ms         BIGINT       NOT NULL,
    created_at         TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_predictions_label_created ON predictions (label, created_at);
CREATE INDEX idx_predictions_created ON predictions (created_at);
