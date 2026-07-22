package com.quantlime.infra.tosspayments;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 토스페이먼츠 웹훅 서명 검증. HMAC-SHA256(webhookSecret, payload)을 base64로
 * 인코딩한 값을 요청 헤더의 서명값과 비교한다. 정확한 헤더명/서명 대상
 * 문자열 조합은 실제 웹훅을 받아보며(또는 토스페이먼츠 웹훅 문서로) 이
 * 클래스를 호출하는 컨트롤러 쪽에서 확정한다 - 이 세션에서는 문서 접근이
 * 막혀 알고리즘(HMAC-SHA256, base64)만 표준 관례대로 구현해뒀다.
 * 시크릿이 비어 있으면(로컬 테스트) 검증을 건너뛴다.
 */
@Slf4j
@Component
public class TossWebhookVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public boolean verify(String payload, String signatureHeader, String webhookSecret) {
        if (!StringUtils.hasText(webhookSecret)) {
            log.warn("토스페이먼츠 웹훅 시크릿 미설정 - 서명 검증을 건너뜁니다");
            return true;
        }
        if (!StringUtils.hasText(signatureHeader)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] received = Base64.getDecoder().decode(signatureHeader);
            return MessageDigest.isEqual(computed, received);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException e) {
            log.error("웹훅 서명 검증 실패: error={}", e.getMessage(), e);
            return false;
        }
    }
}
