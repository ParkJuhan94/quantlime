package com.quantlime.infra.toss;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.toss.dto.TossTokenResponse;
import com.quantlime.infra.toss.exception.TossApiErrorCode;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossTokenManager {

    private static final String REDIS_TOKEN_KEY = "toss:access-token";
    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 3600;

    private final RestClient tossRestClient;
    private final TossApiProperties properties;
    private final StringRedisTemplate redisTemplate;

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
            throw e;
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new ExternalApiException(TossApiErrorCode.RATE_LIMIT_EXCEEDED, e);
        } catch (Exception e) {
            throw new ExternalApiException(TossApiErrorCode.TOKEN_ISSUANCE_FAILED, e);
        }
    }
}
