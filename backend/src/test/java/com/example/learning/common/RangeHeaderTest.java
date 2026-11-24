package com.example.learning.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Range / If-Range 解析的边界用例。
 *
 * <p>这层逻辑决定两件事的正确性：断点续传能不能接上、文件被替换后会不会拼出坏文件。
 * 它不依赖容器，是下载链路里最该被单测覆盖的部分。
 */
class RangeHeaderTest {

    private static final long SIZE = 5000L;

    @Test
    @DisplayName("显式区间按原样解析")
    void explicitRange() {
        RangeHeader.Range r = RangeHeader.parse("bytes=0-1023", SIZE);
        assertEquals(0, r.start());
        assertEquals(1023, r.end());
        assertEquals(1024, r.length());
        assertEquals("bytes=0-1023", r.toHeaderValue());
        assertEquals("bytes 0-1023/5000", r.toContentRange(SIZE));
    }

    @Test
    @DisplayName("开区间一直取到文件末尾")
    void openEndedRange() {
        RangeHeader.Range r = RangeHeader.parse("bytes=1024-", SIZE);
        assertEquals(1024, r.start());
        assertEquals(4999, r.end());
    }

    @Test
    @DisplayName("后缀形式取最后 N 字节")
    void suffixRange() {
        RangeHeader.Range r = RangeHeader.parse("bytes=-500", SIZE);
        assertEquals(4500, r.start());
        assertEquals(4999, r.end());
        assertEquals(500, r.length());
    }

    @Test
    @DisplayName("尾部超过文件大小时截断到末尾，而不是报错")
    void tailClampedToSize() {
        RangeHeader.Range r = RangeHeader.parse("bytes=4990-999999", SIZE);
        assertEquals(4990, r.start());
        assertEquals(4999, r.end());
    }

    @Test
    @DisplayName("起点越过末尾抛 416，并带上真实大小")
    void startBeyondEnd() {
        RangeNotSatisfiableException ex = assertThrows(
                RangeNotSatisfiableException.class,
                () -> RangeHeader.parse("bytes=6000-7000", SIZE));
        assertEquals("bytes */5000", ex.getContentRange());
    }

    @Test
    @DisplayName("开区间起点越界同样要 416，不能被 end<start 判成非法而降级")
    void startBeyondEndOpenEnded() {
        assertThrows(RangeNotSatisfiableException.class,
                () -> RangeHeader.parse("bytes=6000-", SIZE));
        assertThrows(RangeNotSatisfiableException.class,
                () -> RangeHeader.parse("bytes=999999999-", SIZE));
    }

    @Test
    @DisplayName("多段请求降级为全量（S3 不支持多段，直接透传会拿到整份文件）")
    void multiRangeIgnored() {
        assertNull(RangeHeader.parse("bytes=0-10,20-30", SIZE));
    }

    @Test
    @DisplayName("非法格式降级为全量，避免 S3 返回 400 变成 500")
    void malformedIgnored() {
        assertNull(RangeHeader.parse("bytes=abc", SIZE));
        assertNull(RangeHeader.parse("items=0-10", SIZE));
        assertNull(RangeHeader.parse("bytes=100-50", SIZE));
        assertNull(RangeHeader.parse("bytes=", SIZE));
    }

    @Test
    @DisplayName("大小未知时不做分段")
    void unknownSize() {
        assertNull(RangeHeader.parse("bytes=0-1023", null));
        assertNull(RangeHeader.parse("bytes=0-1023", 0L));
    }

    @Test
    @DisplayName("If-Range 相同则允许续传")
    void ifRangeMatches() {
        assertTrue(RangeHeader.ifRangeMatches("\"abc-1\"", "\"abc-1\""));
    }

    @Test
    @DisplayName("If-Range 不同则拒绝续传（否则会拼出内容损坏的文件）")
    void ifRangeMismatches() {
        assertFalse(RangeHeader.ifRangeMatches("\"old-1\"", "\"new-1\""));
    }

    @Test
    @DisplayName("弱校验前缀按 RFC 做弱比较")
    void ifRangeWeakPrefix() {
        assertTrue(RangeHeader.ifRangeMatches("W/\"abc-1\"", "\"abc-1\""));
        assertTrue(RangeHeader.ifRangeMatches("\"abc-1\"", "W/\"abc-1\""));
    }

    @Test
    @DisplayName("HTTP-date 形式不支持，保守回全量")
    void ifRangeDateUnsupported() {
        assertFalse(RangeHeader.ifRangeMatches("Wed, 21 Oct 2015 07:28:00 GMT", "\"abc-1\""));
    }

    @Test
    @DisplayName("缺少任一侧都不允许续传")
    void ifRangeMissingInputs() {
        assertFalse(RangeHeader.ifRangeMatches(null, "\"abc-1\""));
        assertFalse(RangeHeader.ifRangeMatches("\"abc-1\"", null));
        assertFalse(RangeHeader.ifRangeMatches("", ""));
    }
}
