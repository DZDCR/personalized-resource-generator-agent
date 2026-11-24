package com.example.learning.dto.im;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MarkReadRequest(@NotNull Long studentId, @NotNull @Min(0) Long seq) {
}
