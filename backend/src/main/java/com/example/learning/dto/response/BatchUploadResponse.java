package com.example.learning.dto.response;

import java.util.List;

/**
 * 批量导入结果。
 *
 * <p>逐项返回而不是「整批成功/失败」：批量导入的真实场景里，20 个文件常有 1-2 个
 * 因格式或大小不合格，整批回滚会让用户重挑一遍。这里部分成功、逐项给原因。
 */
public record BatchUploadResponse(
        int total,
        int succeeded,
        int failed,
        List<Item> items
) {

    public record Item(String fileName, boolean success, Long sizeBytes, String message) {

        public static Item success(String fileName, long sizeBytes) {
            return new Item(fileName, true, sizeBytes, null);
        }

        public static Item failure(String fileName, String message) {
            return new Item(fileName, false, null, message);
        }
    }
}
