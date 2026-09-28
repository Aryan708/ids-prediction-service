package com.aryan.ids.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/** Stable JSON shape for paged results (instead of serializing Spring's PageImpl). */
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
