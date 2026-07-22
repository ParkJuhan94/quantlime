package com.quantlime.infra.oauth;

import com.quantlime.infra.oauth.dto.OAuthUserInfo;
import com.quantlime.user.domain.OAuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Tag("unit")
class KakaoOAuthClientTest {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USERINFO_URI = "https://kapi.kakao.com/v2/user/me";

    private MockRestServiceServer mockServer;
    private KakaoOAuthClient kakaoOAuthClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        OAuthProperties.Provider provider = new OAuthProperties.Provider(
            "client-id", "client-secret", TOKEN_URI, USERINFO_URI);
        OAuthProperties properties = new OAuthProperties(provider, provider, provider);

        kakaoOAuthClient = new KakaoOAuthClient(restClient, properties);
    }

    @Test
    @DisplayName("[카카오 계정 동의 정보가 모두 있으면 정상적으로 조회한다]")
    void fetch_fullConsent_returnsOAuthUserInfo() {
        // given
        mockServer.expect(requestTo(TOKEN_URI))
            .andExpect(method(POST))
            .andRespond(withSuccess(
                "{\"access_token\":\"kakao-access-token\",\"token_type\":\"bearer\",\"expires_in\":3600}",
                MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USERINFO_URI))
            .andExpect(method(GET))
            .andRespond(withSuccess(
                "{\"id\":123456789,\"kakao_account\":{\"email\":\"test@kakao.com\","
                    + "\"profile\":{\"nickname\":\"카카오유저\",\"profile_image_url\":\"http://pic\"}}}",
                MediaType.APPLICATION_JSON));

        // when
        OAuthUserInfo userInfo = kakaoOAuthClient.fetch("auth-code", "http://localhost/cb");

        // then
        assertThat(userInfo.provider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(userInfo.providerId()).isEqualTo("123456789");
        assertThat(userInfo.email()).isEqualTo("test@kakao.com");
        assertThat(userInfo.nickname()).isEqualTo("카카오유저");
    }

    @Test
    @DisplayName("[이메일/닉네임 동의를 하지 않아도 기본 닉네임으로 조회된다]")
    void fetch_noConsent_fallsBackToDefaultNickname() {
        // given
        mockServer.expect(requestTo(TOKEN_URI))
            .andRespond(withSuccess(
                "{\"access_token\":\"kakao-access-token\",\"token_type\":\"bearer\",\"expires_in\":3600}",
                MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USERINFO_URI))
            .andRespond(withSuccess("{\"id\":987654321}", MediaType.APPLICATION_JSON));

        // when
        OAuthUserInfo userInfo = kakaoOAuthClient.fetch("auth-code", "http://localhost/cb");

        // then
        assertThat(userInfo.providerId()).isEqualTo("987654321");
        assertThat(userInfo.email()).isNull();
        assertThat(userInfo.nickname()).isEqualTo("카카오사용자");
    }

    @Test
    @DisplayName("[닉네임이 빈 문자열이어도 기본 닉네임으로 조회된다]")
    void fetch_blankNickname_fallsBackToDefaultNickname() {
        // given
        mockServer.expect(requestTo(TOKEN_URI))
            .andRespond(withSuccess(
                "{\"access_token\":\"kakao-access-token\",\"token_type\":\"bearer\",\"expires_in\":3600}",
                MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USERINFO_URI))
            .andRespond(withSuccess(
                "{\"id\":111222333,\"kakao_account\":{\"profile\":{\"nickname\":\"\"}}}",
                MediaType.APPLICATION_JSON));

        // when
        OAuthUserInfo userInfo = kakaoOAuthClient.fetch("auth-code", "http://localhost/cb");

        // then
        assertThat(userInfo.providerId()).isEqualTo("111222333");
        assertThat(userInfo.nickname()).isEqualTo("카카오사용자");
    }
}
