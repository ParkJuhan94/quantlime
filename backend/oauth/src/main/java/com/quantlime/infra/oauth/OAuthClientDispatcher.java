package com.quantlime.infra.oauth;

import com.quantlime.infra.oauth.dto.OAuthProfile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuthClientDispatcher {

    private final List<OAuthClient> oAuthClients;

    public boolean supports(String provider) {
        return oAuthClients.stream().anyMatch(client -> client.supports(provider));
    }

    /** 호출 전에 {@link #supports}로 확인해야 한다(미지원 제공자는 호출자의 도메인 예외로 변환). */
    public OAuthProfile fetch(String provider, String code, String redirectUri) {
        return oAuthClients.stream()
            .filter(client -> client.supports(provider))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 OAuth 제공자: " + provider))
            .fetch(code, redirectUri);
    }
}
