package com.quantlime.infra.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.oauth.exception.OAuthErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 카카오·네이버 클라이언트의 실패 경로 - 토큰/사용자 정보 응답 누락, 서버 오류, 서킷 폴백.
 * 정상 조회와 동의 항목 누락은 각 클라이언트의 기존 테스트가 맡는다.
 */
@Tag("unit")
class KakaoNaverOAuthFailureTest {

    private static final String TOKEN_URI = "https://oauth.test/token";
    private static final String USERINFO_URI = "https://oauth.test/userinfo";
    private static final String TOKEN_OK = "{\"access_token\":\"tok\",\"token_type\":\"bearer\",\"expires_in\":3600}";

    private MockRestServiceServer mockServer;
    private KakaoOAuthClient kakao;
    private NaverOAuthClient naver;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        OAuthProperties.Provider provider = new OAuthProperties.Provider("id", "secret", TOKEN_URI, USERINFO_URI);
        OAuthProperties properties = new OAuthProperties(provider, provider, provider);
        kakao = new KakaoOAuthClient(restClient, properties);
        naver = new NaverOAuthClient(restClient, properties);
    }

    private static void assertUserInfoFailed(Throwable t) {
        assertThat(t).isInstanceOf(ExternalApiException.class);
        assertThat(((ExternalApiException) t).getCode()).isEqualTo(OAuthErrorCode.OAUTH_USERINFO_FAILED.getCode());
    }

    @Test
    @DisplayName("[카카오 - 토큰 응답에 access_token이 없으면 사용자 정보 조회 실패로 바꾼다]")
    void kakao_missingAccessToken_fails() {
        mockServer.expect(requestTo(TOKEN_URI)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kakao.fetch("code", "http://cb")).satisfies(KakaoNaverOAuthFailureTest::assertUserInfoFailed);
    }

    @Test
    @DisplayName("[카카오 - 사용자 정보 응답 본문이 비어 있으면 실패로 바꾼다]")
    void kakao_emptyUserInfoBody_fails() {
        mockServer.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USERINFO_URI)).andRespond(withSuccess());

        assertThatThrownBy(() -> kakao.fetch("code", "http://cb")).satisfies(KakaoNaverOAuthFailureTest::assertUserInfoFailed);
    }

    @Test
    @DisplayName("[카카오 - 토큰 서버가 5xx를 돌려주면 실패로 바꾼다]")
    void kakao_tokenServerError_fails() {
        mockServer.expect(requestTo(TOKEN_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> kakao.fetch("code", "http://cb")).satisfies(KakaoNaverOAuthFailureTest::assertUserInfoFailed);
    }

    @Test
    @DisplayName("[네이버 - 토큰 응답에 access_token이 없으면 실패로 바꾼다]")
    void naver_missingAccessToken_fails() {
        mockServer.expect(requestTo(TOKEN_URI)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> naver.fetch("code", "http://cb")).satisfies(KakaoNaverOAuthFailureTest::assertUserInfoFailed);
    }

    @Test
    @DisplayName("[네이버 - 사용자 정보의 response가 비어 있으면 실패로 바꾼다]")
    void naver_emptyResponseNode_fails() {
        mockServer.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USERINFO_URI)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> naver.fetch("code", "http://cb")).satisfies(KakaoNaverOAuthFailureTest::assertUserInfoFailed);
    }

    @Test
    @DisplayName("[네이버 - 사용자 정보 서버가 5xx를 돌려주면 실패로 바꾼다]")
    void naver_userInfoServerError_fails() {
        mockServer.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(TOKEN_OK, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USERINFO_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> naver.fetch("code", "http://cb")).satisfies(KakaoNaverOAuthFailureTest::assertUserInfoFailed);
    }

    @Test
    @DisplayName("[서킷 폴백 - ExternalApiException은 그대로, 그 외 예외는 사용자 정보 조회 실패로 감싼다]")
    void fallback_wrapsNonExternalApiExceptions() {
        ExternalApiException original = new ExternalApiException(OAuthErrorCode.OAUTH_USERINFO_FAILED);

        for (Object client : new Object[] {kakao, naver}) {
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(client, "fetchFallback", "c", "r", original))
                .isSameAs(original);
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                client, "fetchFallback", "c", "r", new IllegalStateException("circuit open")))
                .satisfies(KakaoNaverOAuthFailureTest::assertUserInfoFailed);
        }
    }

    @Test
    @DisplayName("[supports는 자기 제공자 이름에만 true다]")
    void supports_onlyOwnProvider() {
        assertThat(kakao.supports(OAuthProviders.KAKAO)).isTrue();
        assertThat(kakao.supports(OAuthProviders.NAVER)).isFalse();
        assertThat(naver.supports(OAuthProviders.NAVER)).isTrue();
        assertThat(naver.supports(OAuthProviders.KAKAO)).isFalse();
    }
}
