package com.example.learning.auth.service;

import com.example.learning.auth.config.AuthProperties;
import com.example.learning.auth.dto.AuthViews.CaptchaView;
import com.example.learning.auth.security.AuthCodes;
import com.example.learning.common.BizException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * 图形验证码：服务端生成 4 位 SVG，Caffeine 缓存 2 分钟，一次有效。
 * 登录、注册、找回密码均强制校验，防止脚本撞库。
 */
@Service
public class CaptchaService {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final String[] COLORS = {"#1f6feb", "#8250df", "#cf222e", "#1a7f37", "#9a6700", "#0550ae"};

    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final Cache<String, String> codes;

    public CaptchaService(AuthProperties properties) {
        this.properties = properties;
        this.codes = Caffeine.newBuilder()
                .maximumSize(20_000)
                .expireAfterWrite(Duration.ofSeconds(Math.max(30, properties.getCaptcha().getTtlSeconds())))
                .build();
    }

    public CaptchaView create() {
        int length = Math.max(4, properties.getCaptcha().getLength());
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        String captchaId = UUID.randomUUID().toString();
        codes.put(captchaId, code.toString());
        String svg = renderSvg(code.toString());
        String dataUri = "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return new CaptchaView(captchaId, dataUri);
    }

    /**
     * 校验并销毁，避免同一验证码被重放。
     */
    public void verify(String captchaId, String captchaCode) {
        verify(captchaId, captchaCode, true);
    }

    /**
     * @param consume true 表示校验通过后立即销毁；登录流程先传入 false，
     *                在密码校验失败或最终签发成功时再调用 {@link #consume(String)}。
     *                这样 DSD 角色选择重试不需要重新识别验证码，同时错误口令仍会销毁验证码。
     */
    public void verify(String captchaId, String captchaCode, boolean consume) {
        if (!properties.getCaptcha().isEnabled()) {
            return;
        }
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw new BizException(AuthCodes.CAPTCHA_INVALID, "请输入图形验证码");
        }
        String expected = codes.getIfPresent(captchaId);
        if (expected == null || !expected.equalsIgnoreCase(captchaCode.trim())) {
            codes.invalidate(captchaId);
            throw new BizException(AuthCodes.CAPTCHA_INVALID, "图形验证码错误或已过期");
        }
        if (consume) {
            codes.invalidate(captchaId);
        }
    }

    public void consume(String captchaId) {
        if (captchaId != null) {
            codes.invalidate(captchaId);
        }
    }

    private String renderSvg(String code) {
        int width = 132;
        int height = 44;
        StringBuilder svg = new StringBuilder(1024);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
                .append("\" height=\"").append(height).append("\" viewBox=\"0 0 ")
                .append(width).append(' ').append(height).append("\">");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#f6f8fa\" rx=\"6\"/>");
        for (int i = 0; i < 7; i++) {
            svg.append("<line x1=\"").append(random.nextInt(width))
                    .append("\" y1=\"").append(random.nextInt(height))
                    .append("\" x2=\"").append(random.nextInt(width))
                    .append("\" y2=\"").append(random.nextInt(height))
                    .append("\" stroke=\"").append(COLORS[random.nextInt(COLORS.length)])
                    .append("\" stroke-width=\"1\" opacity=\"0.35\"/>");
        }
        for (int i = 0; i < 26; i++) {
            svg.append("<circle cx=\"").append(random.nextInt(width))
                    .append("\" cy=\"").append(random.nextInt(height))
                    .append("\" r=\"1\" fill=\"").append(COLORS[random.nextInt(COLORS.length)])
                    .append("\" opacity=\"0.45\"/>");
        }
        int x = 16;
        for (char c : code.toCharArray()) {
            int rotate = random.nextInt(30) - 15;
            int y = 30 + random.nextInt(6) - 3;
            svg.append("<text x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" font-family=\"Georgia, 'Times New Roman', serif\" font-size=\"27\"")
                    .append(" font-weight=\"700\" fill=\"").append(COLORS[random.nextInt(COLORS.length)])
                    .append("\" transform=\"rotate(").append(rotate).append(' ').append(x).append(' ').append(y)
                    .append(")\">").append(c).append("</text>");
            x += 26 + random.nextInt(4);
        }
        svg.append("</svg>");
        return svg.toString();
    }
}
