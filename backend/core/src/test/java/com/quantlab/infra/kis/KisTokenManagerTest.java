package com.quantlab.infra.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
class KisTokenManagerTest {

    private static final String BASE_URL = "https://kis.test";
    private static final String REDIS_TOKEN_KEY = "kis:access-token";

    private MockRestServiceServer mockServer;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private KisTokenManager kisTokenManager;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        KisApiProperties properties = new KisApiProperties("app-key", "app-secret", BASE_URL, BASE_URL);

        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        kisTokenManager = new KisTokenManager(restClient, properties, redisTemplate);
    }

    @Test
    @DisplayName("[캐시된 토큰이 있으면 API를 호출하지 않고 그대로 반환한다]")
    void getAccessToken_cacheHit_returnsCachedToken() {
        // given
        when(valueOperations.get(REDIS_TOKEN_KEY)).thenReturn("cached-token");

        // when
        String token = kisTokenManager.getAccessToken();

        // then
        assertThat(token).isEqualTo("cached-token");
        mockServer.verify();
    }

    @Test
    @DisplayName("[캐시가 비어있으면 토큰을 새로 발급받아 Redis에 저장한다]")
    void getAccessToken_cacheMiss_issuesNewTokenAndCaches() {
        // given
        when(valueOperations.get(REDIS_TOKEN_KEY)).thenReturn(null);
        mockServer.expect(requestTo(BASE_URL + "/oauth2/tokenP"))
            .andRespond(withSuccess(
                "{\"access_token\":\"new-token\",\"token_type\":\"Bearer\",\"expires_in\":86400}",
                MediaType.APPLICATION_JSON));

        // when
        String token = kisTokenManager.getAccessToken();

        // then
        assertThat(token).isEqualTo("new-token");
        verify(valueOperations).set(REDIS_TOKEN_KEY, "new-token", Duration.ofSeconds(82800));
        mockServer.verify();
    }

    @Test
    @DisplayName("[invalidateToken 호출 시 Redis에 캐시된 토큰을 삭제한다]")
    void invalidateToken_deletesCachedToken() {
        // when
        kisTokenManager.invalidateToken();

        // then
        verify(redisTemplate).delete(REDIS_TOKEN_KEY);
    }
}
