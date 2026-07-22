package com.quantlime.infra.oauth;

import com.quantlime.common.exception.ExternalApiException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Tag("unit")
class GoogleOAuthClientTest {

    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

    private MockRestServiceServer mockServer;
    private GoogleOAuthClient googleOAuthClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        OAuthProperties.Provider provider = new OAuthProperties.Provider(
            "client-id", "client-secret", TOKEN_URI, USERINFO_URI);
        OAuthProperties properties = new OAuthProperties(provider, provider, provider);

        googleOAuthClient = new GoogleOAuthClient(restClient, properties);
    }

    @Test
    @DisplayName("[구글 인가 코드로 사용자 정보를 정상적으로 조회한다]")
    void fetch_success_returnsOAuthUserInfo() {
        // given
        mockServer.expect(requestTo(TOKEN_URI))
            .andExpect(method(POST))
            .andRespond(withSuccess(
                "{\"access_token\":\"google-access-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(USERINFO_URI))
            .andExpect(method(GET))
            .andRespond(withSuccess(
                "{\"sub\":\"google-sub-1\",\"email\":\"test@gmail.com\",\"name\":\"테스트\","
                    + "\"picture\":\"http://pic\"}",
                MediaType.APPLICATION_JSON));

        // when
        OAuthUserInfo userInfo = googleOAuthClient.fetch("auth-code", "http://localhost/cb");

        // then
        assertThat(userInfo.provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(userInfo.providerId()).isEqualTo("google-sub-1");
        assertThat(userInfo.email()).isEqualTo("test@gmail.com");
        assertThat(userInfo.nickname()).isEqualTo("테스트");
        mockServer.verify();
    }

    @Test
    @DisplayName("[토큰 교환에 실패하면 ExternalApiException이 발생한다]")
    void fetch_tokenExchangeFails_throwsExternalApiException() {
        // given
        mockServer.expect(requestTo(TOKEN_URI)).andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> googleOAuthClient.fetch("auth-code", "http://localhost/cb"))
            .isInstanceOf(ExternalApiException.class);
    }
}
