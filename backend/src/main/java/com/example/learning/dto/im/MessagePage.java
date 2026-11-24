package com.example.learning.dto.im;

import java.util.List;

public record MessagePage(List<MessageDto> messages, Long lastSeq, boolean hasMore) {
}
