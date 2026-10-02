package com.quantlime.subscription.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 빌링키는 카드 자동결제 권한을 대표하는 민감정보라 DB에는 AES-GCM 암호문만 남아야 한다. 평문 통과(키 미설정),
 * 암복호화 왕복, 무작위 IV, 변조 탐지, 잘못된 키 처리를 고정한다.
 */
@Tag("unit")
class BillingKeyConverterTest {

    private static final String KEY_A = keyOf((byte) 1);
    private static final String KEY_B = keyOf((byte) 2);

    private static String keyOf(byte fill) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, fill);
        return Base64.getEncoder().encodeToString(key);
    }

    private BillingKeyConverter converter(String keyBase64) {
        BillingKeyConverter converter = new BillingKeyConverter();
        ReflectionTestUtils.setField(converter, "encryptionKeyBase64", keyBase64);
        return converter;
    }

    @Test
    @DisplayName("[null은 키 설정과 무관하게 양방향 모두 null이다]")
    void nullStaysNull() {
        BillingKeyConverter withKey = converter(KEY_A);
        BillingKeyConverter withoutKey = converter("");

        assertThat(withKey.convertToDatabaseColumn(null)).isNull();
        assertThat(withKey.convertToEntityAttribute(null)).isNull();
        assertThat(withoutKey.convertToDatabaseColumn(null)).isNull();
        assertThat(withoutKey.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("[키가 설정되지 않으면(로컬 개발) 평문이 그대로 통과한다 - 운영에서는 반드시 키를 채워야 한다]")
    void blankKey_passesThroughPlaintext() {
        BillingKeyConverter converter = converter("   ");

        assertThat(converter.convertToDatabaseColumn("billing-key-123")).isEqualTo("billing-key-123");
        assertThat(converter.convertToEntityAttribute("billing-key-123")).isEqualTo("billing-key-123");
    }

    @Test
    @DisplayName("[암호화한 값은 평문과 다르고 복호화하면 원문으로 돌아온다(한글 포함)]")
    void roundTrip_restoresOriginal() {
        BillingKeyConverter converter = converter(KEY_A);

        for (String plain : new String[] {"billing-key-123", "빌링키-한글-테스트", "x"}) {
            String stored = converter.convertToDatabaseColumn(plain);

            assertThat(stored).isNotEqualTo(plain).doesNotContain(plain);
            assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(plain);
        }
    }

    @Test
    @DisplayName("[저장 값은 base64(IV 12바이트 + 암호문 + 인증태그 16바이트) 구조다]")
    void storedFormat_isIvPlusCiphertextPlusTag() {
        String plain = "billing-key-123";

        byte[] combined = Base64.getDecoder().decode(converter(KEY_A).convertToDatabaseColumn(plain));

        assertThat(combined).hasSize(12 + plain.getBytes(StandardCharsets.UTF_8).length + 16);
    }

    @Test
    @DisplayName("[같은 평문도 호출마다 다른 암호문이 나온다 - 무작위 IV로 값 비교 공격을 막는다]")
    void sameInput_producesDifferentCiphertext() {
        BillingKeyConverter converter = converter(KEY_A);

        String first = converter.convertToDatabaseColumn("billing-key-123");
        String second = converter.convertToDatabaseColumn("billing-key-123");

        assertThat(first).isNotEqualTo(second);
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo(converter.convertToEntityAttribute(second));
    }

    @Test
    @DisplayName("[암호문이 한 비트라도 변조되면 복호화가 실패한다(GCM 인증태그)]")
    void tamperedCiphertext_isRejected() {
        BillingKeyConverter converter = converter(KEY_A);
        byte[] combined = Base64.getDecoder().decode(converter.convertToDatabaseColumn("billing-key-123"));
        combined[combined.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(combined);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("복호화");
    }

    @Test
    @DisplayName("[다른 키로는 복호화할 수 없다]")
    void wrongKey_cannotDecrypt() {
        String stored = converter(KEY_A).convertToDatabaseColumn("billing-key-123");

        assertThatThrownBy(() -> converter(KEY_B).convertToEntityAttribute(stored))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("[base64가 아니거나 IV 길이보다 짧은 저장 값은 IllegalStateException이다]")
    void malformedStoredValue_isRejected() {
        BillingKeyConverter converter = converter(KEY_A);

        assertThatThrownBy(() -> converter.convertToEntityAttribute("not base64 !!"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> converter.convertToEntityAttribute(Base64.getEncoder().encodeToString(new byte[5])))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("[키 길이가 AES가 허용하지 않는 값이면 암호화가 IllegalStateException으로 실패한다(조용히 평문 저장하지 않는다)]")
    void invalidKeyLength_failsToEncrypt() {
        BillingKeyConverter converter = converter(Base64.getEncoder().encodeToString(new byte[10]));

        assertThatThrownBy(() -> converter.convertToDatabaseColumn("billing-key-123"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("암호화");
    }
}
