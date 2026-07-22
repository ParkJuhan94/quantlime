package com.quantlime.auth.service;

import com.quantlime.auth.dto.mapper.AuthMapper;
import com.quantlime.auth.dto.request.SocialLoginRequest;
import com.quantlime.auth.exception.AuthErrorCode;
import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.auth.token.RefreshTokenStore;
import com.quantlime.common.exception.UnauthorizedException;
import com.quantlime.infra.oauth.OAuthClientDispatcher;
import com.quantlime.infra.oauth.dto.OAuthUserInfo;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.service.UserService;
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
