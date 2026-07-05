package com.quantlab.infra.oauth;

import com.quantlab.infra.oauth.dto.OAuthUserInfo;
import com.quantlab.user.domain.OAuthProvider;
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
class NaverOAuthClientTest {

    private static final String TOKEN_URI = "https://nid.naver.com/oauth2.0/token";
    private static final String USERINFO_URI = "https://openapi.naver.com/v1/nid/me";

    private MockRestServiceServer mockServer;
    private NaverOAuthClient naverOAuthClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        OAuthProperties.Provider provider = new OAuthProperties.Provider(
            "client-id", "client-secret", "http://localhost/cb", TOKEN_URI, USERINFO_URI);
        OAuthProperties properties = new OAuthProperties(provider, provider, provider);

        naverOAuthClient = new NaverOAuthClient(restClient, properties);
    }

    @Test
    @DisplayName("[네이버 인가 코드로 사용자 정보를 정상적으로 조회한다]")
    void fetch_success_returnsOAuthUserInfo() {
        // given
        mockServer.expect(requestTo(TOKEN_URI))
            .andExpect(method(POST))
            .andRespond(withSuccess(
                "{\"access_token\":\"naver-access-token\",\"token_type\":\"bearer\",\"expires_in\":3600}",
                MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USERINFO_URI))
            .andExpect(method(GET))
            .andRespond(withSuccess(
                "{\"resultcode\":\"00\",\"message\":\"success\",\"response\":{\"id\":\"naver-id-1\","
                    + "\"email\":\"test@naver.com\",\"nickname\":\"네이버유저\","
                    + "\"profile_image\":\"http://pic\"}}",
                MediaType.APPLICATION_JSON));

        // when
        OAuthUserInfo userInfo = naverOAuthClient.fetch("auth-code", "http://localhost/cb");

        // then
        assertThat(userInfo.provider()).isEqualTo(OAuthProvider.NAVER);
        assertThat(userInfo.providerId()).isEqualTo("naver-id-1");
        assertThat(userInfo.email()).isEqualTo("test@naver.com");
        assertThat(userInfo.nickname()).isEqualTo("네이버유저");
    }
}
