package com.example.learning.dto.im;

import jakarta.validation.constraints.NotNull;

public record CreateDirectRequest(@NotNull Long studentId, @NotNull Long peerId) {
}
