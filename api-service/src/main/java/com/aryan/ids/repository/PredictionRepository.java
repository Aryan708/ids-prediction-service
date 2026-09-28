package com.aryan.ids.repository;

import com.aryan.ids.domain.Label;
import com.aryan.ids.domain.Prediction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    Page<Prediction> findByLabel(Label label, Pageable pageable);

    long countByLabel(Label label);

    @Query("select avg(p.latencyMs) from Prediction p")
    Double averageLatencyMs();
}
