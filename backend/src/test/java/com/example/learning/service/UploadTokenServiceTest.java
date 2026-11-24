package com.example.learning.service;

import com.example.learning.common.BizException;
import com.example.learning.config.UploadProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 上传凭证的签发与校验（纯内存，不依赖 Spring 上下文）。
 */
class UploadTokenServiceTest {

    private UploadTokenService service(long ttlMinutes) {
        UploadProperties properties = new UploadProperties();
        properties.getSecurity().setSigningKey("unit-test-signing-key-32-bytes!!");
        properties.getSecurity().setTokenTtlMinutes(ttlMinutes);
        return new UploadTokenService(properties);
    }

    @Test
    void issueAndVerify() {
        UploadTokenService service = service(120);
        UploadTokenService.IssuedToken issued = service.issue(42L);

        assertEquals(42L, service.verify(issued.token()));
    }

    @Test
    void rejectsTamperedStudentId() {
        UploadTokenService service = service(120);
        String token = service.issue(42L).token();
        String[] parts = token.split("\\.");
        parts[1] = "43";
        String tampered = String.join(".", parts);

        BizException ex = assertThrows(BizException.class, () -> service.verify(tampered));
        assertEquals(401, ex.getCode());
    }

    @Test
    void rejectsExpiredToken() {
        UploadTokenService service = service(-1);
        String token = service.issue(42L).token();

        BizException ex = assertThrows(BizException.class, () -> service.verify(token));
        assertEquals(401, ex.getCode());
    }

    @Test
    void rejectsMalformedToken() {
        UploadTokenService service = service(120);

        assertThrows(BizException.class, () -> service.verify(null));
        assertThrows(BizException.class, () -> service.verify("not-a-token"));
        assertThrows(BizException.class, () -> service.verify("v2.1.1.abc"));
    }
}
