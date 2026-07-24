package com.quantlime.infra.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.common.exception.ExternalApiException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@Tag("unit")
class TossTokenManagerTest {

    private static final String BASE_URL = "https://toss.test";
    private static final String REDIS_TOKEN_KEY = "toss:access-token";

    private MockRestServiceServer mockServer;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private TossTokenManager tossTokenManager;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        TossApiProperties properties = new TossApiProperties("client-id", "client-secret", BASE_URL);

        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tossTokenManager = new TossTokenManager(restClient, properties, redisTemplate);
    }

    @Test
    @DisplayName("[캐시된 토큰이 있으면 API를 호출하지 않고 그대로 반환한다]")
    void getAccessToken_cacheHit_returnsCachedToken() {
        // given
        when(valueOperations.get(REDIS_TOKEN_KEY)).thenReturn("cached-token");

        // when
        String token = tossTokenManager.getAccessToken();

        // then
        assertThat(token).isEqualTo("cached-token");
        mockServer.verify();
    }

    @Test
    @DisplayName("[캐시가 비어있으면 토큰을 새로 발급받아 Redis에 저장한다]")
    void getAccessToken_cacheMiss_issuesNewTokenAndCaches() {
        // given
        when(valueOperations.get(REDIS_TOKEN_KEY)).thenReturn(null);
        mockServer.expect(requestTo(BASE_URL + "/oauth2/token"))
            .andRespond(withSuccess(
                "{\"access_token\":\"new-token\",\"token_type\":\"Bearer\",\"expires_in\":86400}",
                MediaType.APPLICATION_JSON));

        // when
        String token = tossTokenManager.getAccessToken();

        // then
        assertThat(token).isEqualTo("new-token");
        verify(valueOperations).set(REDIS_TOKEN_KEY, "new-token", Duration.ofSeconds(82800));
        mockServer.verify();
    }

    @Test
    @DisplayName("[invalidateToken 호출 시 Redis에 캐시된 토큰을 삭제한다]")
    void invalidateToken_deletesCachedToken() {
        // when
        tossTokenManager.invalidateToken();

        // then
        verify(redisTemplate).delete(REDIS_TOKEN_KEY);
    }

    @Test
    @DisplayName("[토큰 발급 실패 후 백오프 기간 안에 재호출하면 토스를 다시 부르지 않고 즉시 실패한다]")
    void getAccessToken_afterFailure_doesNotRetryWithinBackoff() {
        // given: 토큰 엔드포인트가 1회 400으로 실패
        when(valueOperations.get(REDIS_TOKEN_KEY)).thenReturn(null);
        mockServer.expect(requestTo(BASE_URL + "/oauth2/token"))
            .andRespond(withBadRequest());

        // when: 백오프 기간 안에 바로 재호출
        assertThatThrownBy(tossTokenManager::getAccessToken)
            .isInstanceOf(ExternalApiException.class);
        assertThatThrownBy(tossTokenManager::getAccessToken)
            .isInstanceOf(ExternalApiException.class);

        // then: 토스 호출은 최초 1회만 발생(두 번째는 백오프로 즉시 실패,
        // MockRestServiceServer의 단일 expect()가 그대로 충족됨)
        mockServer.verify();
    }
}
