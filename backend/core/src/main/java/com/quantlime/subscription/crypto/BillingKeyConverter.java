package com.quantlime.subscription.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 빌링키는 카드사 자동결제 권한을 대표하는 민감정보라 DB 컬럼에 평문
 * 저장하지 않고 AES-GCM으로 암호화한다. 암호화 키(SUBSCRIPTION_BILLING_KEY_ENCRYPTION_KEY,
 * base64 32바이트)가 설정되지 않은 로컬 개발 환경에서는 평문 그대로
 * 통과시킨다(테스트 키 환경이라 실질적 위험이 없고, 개발 편의를 위해
 * TossApiConfig 등 기존 외부 연동과 동일하게 "미설정 시 경고만" 정책을
 * 따름) - 운영 배포 시엔 반드시 채워야 한다.
 */
@Slf4j
@Component
@Converter
public class BillingKeyConverter implements AttributeConverter<String, String> {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    @Value("${subscription.billing-key-encryption-key:}")
    private String encryptionKeyBase64;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        if (!StringUtils.hasText(encryptionKeyBase64)) {
            return attribute;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("빌링키 암호화 실패: error={}", e.getMessage(), e);
            throw new IllegalStateException("빌링키 암호화에 실패했습니다.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (!StringUtils.hasText(encryptionKeyBase64)) {
            return dbData;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(dbData);
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("빌링키 복호화 실패: error={}", e.getMessage(), e);
            throw new IllegalStateException("빌링키 복호화에 실패했습니다.", e);
        }
    }

    private SecretKeySpec secretKey() {
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        return new SecretKeySpec(keyBytes, "AES");
    }
}
