package com.quantlime.infra.toss;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.toss.dto.TossTokenResponse;
import com.quantlime.infra.toss.exception.TossApiErrorCode;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 토큰 발급(POST /oauth2/token)은 시세 조회 API보다 훨씬 빡빡한 자체 레이트리밋을
 * 쓰고(실측 {@code x-ratelimit-limit: 5}, 시세 API는 스펙 예시 10건/초), Akamai Bot
 * Manager로 추정되는 봇 차단 뒤에 있다(실측 응답에 {@code ak_bmsc} 쿠키 확인 - 이
 * 프로젝트가 FRED 연동에서 이미 겪은 것과 동일한 종류의 차단, CLAUDE.md 참고).
 * 이 메서드는 Redis 캐시 미스 시 이 프로젝트의 거의 모든 Toss 호출(캘린더/환율/시세)이
 * 공통으로 거쳐가는 단일 지점인데, 과거엔 실패해도 재시도 간격을 강제하지 않아 캐시가
 * 안 채워지는 동안 모든 소비자가 각자 다시 이 메서드를 호출 - 429가 아니라 Akamai
 * 차단(깨진 바이너리 400 응답)을 유발·연장하는 실제 장애로 이어졌다(실측). 백오프 값을
 * {@link com.quantlime.price.cache.MarketCalendarCache}보다 길게 잡은 이유도 이
 * 차이 때문 - 시세 API는 짧게 재시도해도 안전하지만, 이미 봇 차단이 걸린 상태에서
 * 짧은 간격으로 재시도하면 차단이 오히려 갱신·연장될 위험이 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossTokenManager {

    private static final String REDIS_TOKEN_KEY = "toss:access-token";
    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 3600;
    private static final long FAILURE_BACKOFF_SECONDS = 30;

    private final RestClient tossRestClient;
    private final TossApiProperties properties;
    private final StringRedisTemplate redisTemplate;

    private volatile Instant retryNotBefore = Instant.MIN;

    public String getAccessToken() {
        String cachedToken = redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
        if (cachedToken != null) {
            return cachedToken;
        }
        return refreshToken();
    }

    /**
     * 토스 API가 캐시된 토큰을 401(invalid-token)로 거부할 때 호출한다.
     * 토큰은 계정당 1개만 유효해 다른 프로세스의 재발급이 이 캐시를 조용히
     * 무효화시킬 수 있는데, TTL만으로는 이를 감지할 수 없기 때문이다.
     */
    public void invalidateToken() {
        redisTemplate.delete(REDIS_TOKEN_KEY);
    }

    private synchronized String refreshToken() {
        String cachedToken = redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
        if (cachedToken != null) {
            return cachedToken;
        }
        if (Instant.now().isBefore(retryNotBefore)) {
            throw new ExternalApiException(TossApiErrorCode.TOKEN_ISSUANCE_FAILED);
        }

        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "client_credentials");
            formData.add("client_id", properties.getClientId());
            formData.add("client_secret", properties.getClientSecret());

            TossTokenResponse response = tossRestClient.post()
                .uri("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(TossTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new ExternalApiException(TossApiErrorCode.TOKEN_ISSUANCE_FAILED);
            }

            long ttl = response.expiresIn() - TOKEN_REFRESH_MARGIN_SECONDS;
            redisTemplate.opsForValue().set(
                REDIS_TOKEN_KEY,
                response.accessToken(),
                Duration.ofSeconds(ttl)
            );

            log.info("토스증권 API 토큰 갱신 완료: expiresIn={}초", response.expiresIn());
            return response.accessToken();
        } catch (ExternalApiException e) {
            retryNotBefore = Instant.now().plusSeconds(FAILURE_BACKOFF_SECONDS);
            throw e;
        } catch (HttpClientErrorException.TooManyRequests e) {
            retryNotBefore = Instant.now().plusSeconds(FAILURE_BACKOFF_SECONDS);
            throw new ExternalApiException(TossApiErrorCode.RATE_LIMIT_EXCEEDED, e);
        } catch (Exception e) {
            retryNotBefore = Instant.now().plusSeconds(FAILURE_BACKOFF_SECONDS);
            throw new ExternalApiException(TossApiErrorCode.TOKEN_ISSUANCE_FAILED, e);
        }
    }
}
