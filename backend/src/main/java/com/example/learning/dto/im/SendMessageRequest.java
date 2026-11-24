package com.example.learning.dto.im;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotNull Long studentId,
        @NotBlank @Size(max = 64) String clientMsgId,
        @NotBlank @Size(max = 4000) String content
) {
}
