package com.quantlab.auth.service;

import com.quantlab.auth.dto.mapper.AuthMapper;
import com.quantlab.auth.dto.request.SocialLoginRequest;
import com.quantlab.auth.exception.AuthErrorCode;
import com.quantlab.auth.jwt.JwtTokenProvider;
import com.quantlab.auth.token.RefreshTokenStore;
import com.quantlab.common.exception.UnauthorizedException;
import com.quantlab.infra.oauth.OAuthClientDispatcher;
import com.quantlab.infra.oauth.dto.OAuthUserInfo;
import com.quantlab.user.domain.OAuthProvider;
import com.quantlab.user.domain.User;
import com.quantlab.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final OAuthClientDispatcher oAuthClientDispatcher;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    @Transactional
    public AuthTokens login(OAuthProvider provider, SocialLoginRequest request) {
        OAuthUserInfo userInfo = oAuthClientDispatcher.fetch(
            provider, request.code(), request.redirectUri());
        User user = userService.findOrCreate(userInfo);
        log.info("소셜 로그인 완료: userId={}, provider={}", user.getId(), provider);
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthTokens reissue(String refreshToken) {
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new UnauthorizedException(AuthErrorCode.INVALID_TOKEN);
        }

        Long userId = jwtTokenProvider.validateAndGetUserId(refreshToken);
        String savedRefreshToken = refreshTokenStore.findByUserId(userId)
            .orElseThrow(() -> new UnauthorizedException(AuthErrorCode.MISMATCH_REFRESH_TOKEN));
        if (!savedRefreshToken.equals(refreshToken)) {
            throw new UnauthorizedException(AuthErrorCode.MISMATCH_REFRESH_TOKEN);
        }

        User user = userService.getById(userId);
        return issueTokens(user);
    }

    public void logout(Long userId) {
        refreshTokenStore.delete(userId);
        log.info("로그아웃 완료: userId={}", userId);
    }

    private AuthTokens issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenStore.save(user.getId(), refreshToken);
        return new AuthTokens(
            AuthMapper.toTokenResponse(accessToken, jwtTokenProvider.getAccessTokenValidity()),
            refreshToken);
    }
}
