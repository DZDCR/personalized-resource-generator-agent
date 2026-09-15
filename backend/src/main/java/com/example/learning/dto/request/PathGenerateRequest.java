package com.example.learning.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PathGenerateRequest(
        @NotBlank String studentId,
        @NotBlank String targetTopic
) {}