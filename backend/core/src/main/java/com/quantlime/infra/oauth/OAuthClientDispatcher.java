package com.quantlime.infra.oauth;

import com.quantlime.auth.exception.AuthErrorCode;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.infra.oauth.dto.OAuthUserInfo;
import com.quantlime.user.domain.OAuthProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuthClientDispatcher {

    private final List<OAuthClient> oAuthClients;

    public OAuthUserInfo fetch(OAuthProvider provider, String code, String redirectUri) {
        OAuthClient client = oAuthClients.stream()
            .filter(oAuthClient -> oAuthClient.supports(provider))
            .findFirst()
            .orElseThrow(() -> new ValidationException(AuthErrorCode.UNSUPPORTED_PROVIDER));
        return client.fetch(code, redirectUri);
    }
}
