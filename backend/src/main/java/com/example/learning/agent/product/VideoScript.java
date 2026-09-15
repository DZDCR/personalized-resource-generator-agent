package com.example.learning.agent.product;

import java.util.List;

/**
 * ScriptAgent 产出的教学视频分镜脚本。
 */
public record VideoScript(
        String topic,
        List<VideoSegment> segments
) {

    public record VideoSegment(
            int segmentNo,
            String title,
            int durationSec,
            String sceneType,
            String narration,
            String visualSuggestion
    ) {}
}