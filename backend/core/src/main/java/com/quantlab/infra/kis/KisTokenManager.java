package com.quantlab.infra.kis;

import com.quantlab.common.exception.ExternalApiException;
import com.quantlab.infra.kis.dto.KisTokenRequest;
import com.quantlab.infra.kis.dto.KisTokenResponse;
import com.quantlab.infra.kis.exception.KisApiErrorCode;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * KIS(한국투자증권) API 토큰 발급/캐싱. 구조는 {@code TossTokenManager}와
 * 동일(Redis TTL 캐시 + 401 감지 시 무효화 후 1회 재시도)하되, KIS는
 * 발급 엔드포인트가 form-urlencoded가 아닌 JSON 바디(/oauth2/tokenP)라는
 * 점만 다르다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisTokenManager {

    private static final String REDIS_TOKEN_KEY = "kis:access-token";
    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 3600;

    private final RestClient kisRestClient;
    private final KisApiProperties properties;
    private final StringRedisTemplate redisTemplate;

    public String getAccessToken() {
        String cachedToken = redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
        if (cachedToken != null) {
            return cachedToken;
        }
        return refreshToken();
    }

    /**
     * KIS API가 캐시된 토큰을 401로 거부할 때 호출한다. 만료 이전에
     * 서버 측 사유로 토큰이 거부되는 경우(예: 재발급 정책, 세션 정리)에
     * 대비해 TTL과 무관하게 캐시를 지우고 재발급받을 수 있게 한다
     * (토스 토큰 401 무효화와 동일한 방어 패턴, TossTokenManager 참고).
     */
    public void invalidateToken() {
        redisTemplate.delete(REDIS_TOKEN_KEY);
    }

    private synchronized String refreshToken() {
        String cachedToken = redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
        if (cachedToken != null) {
            return cachedToken;
        }

        try {
            KisTokenRequest request = KisTokenRequest.of(
                properties.getAppKey(), properties.getAppSecret());

            KisTokenResponse response = kisRestClient.post()
                .uri("/oauth2/tokenP")
                .body(request)
                .retrieve()
                .body(KisTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new ExternalApiException(KisApiErrorCode.TOKEN_ISSUANCE_FAILED);
            }

            long ttl = response.expiresIn() - TOKEN_REFRESH_MARGIN_SECONDS;
            redisTemplate.opsForValue().set(
                REDIS_TOKEN_KEY,
                response.accessToken(),
                Duration.ofSeconds(ttl)
            );

            log.info("KIS API 토큰 갱신 완료: expiresIn={}초", response.expiresIn());
            return response.accessToken();
        } catch (ExternalApiException e) {
            throw e;
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new ExternalApiException(KisApiErrorCode.RATE_LIMIT_EXCEEDED, e);
        } catch (Exception e) {
            throw new ExternalApiException(KisApiErrorCode.TOKEN_ISSUANCE_FAILED, e);
        }
    }
}
