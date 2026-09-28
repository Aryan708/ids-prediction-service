package com.aryan.ids.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BatchRequest(
        @NotEmpty
        @Size(max = 500)
        List<@Valid @NotNull FlowRequest> flows) {
}
