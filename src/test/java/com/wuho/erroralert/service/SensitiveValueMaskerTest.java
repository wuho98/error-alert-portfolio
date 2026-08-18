package com.wuho.erroralert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SensitiveValueMaskerTest {

    private final SensitiveValueMasker masker = new SensitiveValueMasker();

    @Test
    void masksWebhookUrl() {
        String result = masker.mask("발송 실패 url=https://discord.com/api/webhooks/1234/abcd 재시도 예정");

        assertEquals("발송 실패 url=[MASKED] 재시도 예정", result);
    }

    @Test
    void masksApiKey() {
        String result = masker.mask("인증 헤더 pk_live_a1b2c3 로 요청함");

        assertEquals("인증 헤더 [MASKED] 로 요청함", result);
    }

    @Test
    void keepsNormalTextUntouched() {
        String result = masker.mask("PG_TIMEOUT 27건 발생, 기준 10건");

        assertEquals("PG_TIMEOUT 27건 발생, 기준 10건", result);
    }

    @Test
    void masksBearerToken() {
        String result = masker.mask("인증 헤더 Bearer eyJhbGciOi.abc123 포함됨");

        assertEquals("인증 헤더 [MASKED] 포함됨", result);
    }
}
