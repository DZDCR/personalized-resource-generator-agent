package com.example.learning.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QuizSubmitRequest(
        @NotBlank String studentId,
        @NotNull Long resourceId,
        @NotNull java.util.List<String> answers,
        Integer durationSec
) {}