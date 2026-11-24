package com.example.learning.dto.im;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateGroupRequest(
        @NotNull Long studentId,
        @NotBlank @Size(max = 128) String title,
        @NotEmpty List<Long> memberIds
) {
}
