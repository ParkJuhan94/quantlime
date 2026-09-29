package com.quantlime.infra.tosspayments;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class TossWebhookVerifierTest {

    private static final String SECRET = "webhook-secret";
    private static final String PAYLOAD = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"}";

    private static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("[서명이 시크릿으로 계산한 값과 일치하면 유효로 판정한다]")
    void verify_validSignature_returnsTrue() {
        // given
        TossWebhookVerifier verifier = new TossWebhookVerifier(false);
        String signature = sign(PAYLOAD, SECRET);

        // when
        boolean valid = verifier.verify(PAYLOAD, signature, SECRET);

        // then
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("[서명이 계산값과 다르면 무효로 판정한다]")
    void verify_invalidSignature_returnsFalse() {
        // given
        TossWebhookVerifier verifier = new TossWebhookVerifier(false);
        String tampered = sign("{\"eventType\":\"TAMPERED\"}", SECRET);

        // when
        boolean valid = verifier.verify(PAYLOAD, tampered, SECRET);

        // then
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("[서명 헤더가 비어 있으면 무효로 판정한다]")
    void verify_missingSignatureHeader_returnsFalse() {
        // given
        TossWebhookVerifier verifier = new TossWebhookVerifier(false);

        // when
        boolean valid = verifier.verify(PAYLOAD, null, SECRET);

        // then
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("[시크릿이 비어 있고 skip opt-out(prod 기본)이면 fail-closed로 거부한다]")
    void verify_missingSecret_failClosedByDefault_returnsFalse() {
        // given: prod 기본값 - 시크릿이 실수로 비어도 서명 없는 웹훅을 통과시키지 않는다
        TossWebhookVerifier verifier = new TossWebhookVerifier(false);

        // when
        boolean valid = verifier.verify(PAYLOAD, "any-signature", "");

        // then
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("[시크릿이 비어 있어도 skip opt-in(로컬 개발)이면 검증을 건너뛰고 통과시킨다]")
    void verify_missingSecret_allowMissingOptIn_returnsTrue() {
        // given: 로컬 개발 편의용 명시적 opt-in
        TossWebhookVerifier verifier = new TossWebhookVerifier(true);

        // when
        boolean valid = verifier.verify(PAYLOAD, null, "");

        // then
        assertThat(valid).isTrue();
    }
}
