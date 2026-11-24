package com.example.learning.common;

/**
 * HTTP Range 请求头解析（只做单段，够下载器与播放器用）。
 *
 * <p>为什么不把 Range 头直接透传给 S3：S3 只认单段，遇到多段（{@code bytes=0-99,200-299}）
 * 会忽略 Range 并返回 200 全量，遇到非法格式（{@code bytes=abc}）会返回 400。
 * 前者让客户端以为拿到了分段数据、实际是整份文件；后者会被当作服务端故障变成 500。
 * 在这里先解析一遍，把「不支持」的形态统一降级成「无 Range」，行为就可预期了。
 *
 * <p>三种合法写法都支持：
 * <ul>
 *   <li>{@code bytes=0-1023} —— 显式区间</li>
 *   <li>{@code bytes=1024-} —— 从 1024 到文件末尾（断点续传最常用）</li>
 *   <li>{@code bytes=-1024} —— 最后 1024 字节</li>
 * </ul>
 */
public final class RangeHeader {

    private RangeHeader() {
    }

    /** 解析结果：闭区间 [start, end]。 */
    public record Range(long start, long end) {
        public long length() {
            return end - start + 1;
        }

        /** 回传给 S3 的 Range 值。 */
        public String toHeaderValue() {
            return "bytes=" + start + "-" + end;
        }

        /** 响应头 Content-Range 的值。 */
        public String toContentRange(long totalSize) {
            return "bytes " + start + "-" + end + "/" + totalSize;
        }
    }

    /**
     * 解析 Range 头。
     *
     * @param header    原始头值，可为 null
     * @param totalSize 对象总字节数，未知时传 null 或非正数
     * @return 需要分段时返回区间；返回 null 表示「忽略 Range，按全量响应」
     * @throws RangeNotSatisfiableException 起点已越过文件末尾（应响应 416）
     */
    public static Range parse(String header, Long totalSize) {
        if (header == null || header.isBlank() || totalSize == null || totalSize <= 0) {
            return null;
        }
        String value = header.trim();
        if (!value.regionMatches(true, 0, "bytes=", 0, 6)) {
            return null;
        }
        String spec = value.substring(6).trim();
        // 多段请求不支持：降级为全量，RFC 9110 允许服务端忽略 Range
        if (spec.indexOf(',') >= 0) {
            return null;
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String rawStart = spec.substring(0, dash).trim();
        String rawEnd = spec.substring(dash + 1).trim();
        try {
            if (rawStart.isEmpty()) {
                // 后缀形式：bytes=-500
                if (rawEnd.isEmpty()) {
                    return null;
                }
                long suffix = Long.parseLong(rawEnd);
                if (suffix <= 0) {
                    return null;
                }
                long start = Math.max(0, totalSize - suffix);
                return new Range(start, totalSize - 1);
            }
            long start = Long.parseLong(rawStart);
            if (start < 0) {
                return null;
            }
            // 必须先判越界再判 end < start：开区间 bytes=999999- 的 end 会被填成
            // totalSize-1，若先比较就会误判成「区间非法」而降级为 200 全量，
            // 客户端拿到的不是 416，会以为服务端不支持分段，续传逻辑因此出错。
            if (start >= totalSize) {
                throw new RangeNotSatisfiableException(totalSize);
            }
            long end = rawEnd.isEmpty() ? totalSize - 1 : Long.parseLong(rawEnd);
            if (end < start) {
                return null;
            }
            // 越界尾部按规范截断到文件末尾，而不是报错
            return new Range(start, Math.min(end, totalSize - 1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 判断 If-Range 是否与当前对象一致。
     *
     * <p>这是断点续传的正确性前提：客户端带着上次的 ETag 与 {@code Range} 发起续传，
     * 若文件在此期间被替换，服务端必须回 200 全量——否则客户端会把新文件的片段
     * 拼到旧文件后面，得到一个大小正确、内容损坏的文件。
     *
     * <p>只支持 ETag 形式。HTTP-date 形式一律视为不匹配（回全量），因为对象时间戳
     * 由存储端维护，与客户端手里的 Last-Modified 精度对不齐，误判为「匹配」的代价
     * 远大于多传一次。
     */
    public static boolean ifRangeMatches(String ifRange, String currentEtag) {
        if (ifRange == null || ifRange.isBlank() || currentEtag == null || currentEtag.isBlank()) {
            return false;
        }
        String candidate = ifRange.trim();
        // HTTP-date：形如 Wed, 21 Oct 2015 07:28:00 GMT，不支持
        if (!candidate.startsWith("\"") && !candidate.startsWith("W/")) {
            return false;
        }
        return stripWeak(candidate).equals(stripWeak(currentEtag.trim()));
    }

    private static String stripWeak(String etag) {
        return etag.startsWith("W/") ? etag.substring(2) : etag;
    }
}
