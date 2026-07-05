package com.quantlab.common.controller;

import com.quantlab.auth.dto.mapper.AuthMapper;
import com.quantlab.auth.dto.response.TokenResponse;
import com.quantlab.auth.jwt.JwtTokenProvider;
import com.quantlab.auth.token.RefreshTokenStore;
import com.quantlab.infra.oauth.dto.OAuthUserInfo;
import com.quantlab.price.scheduler.OhlcvCollectorScheduler;
import com.quantlab.user.domain.OAuthProvider;
import com.quantlab.user.domain.User;
import com.quantlab.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "개발용 API")
@Profile("dev")
@RestController
@RequiredArgsConstructor
@RequestMapping("/dev")
public class DevController {

    private static final String DEV_TEST_PROVIDER_ID = "dev-test-user";

    private final OhlcvCollectorScheduler ohlcvCollectorScheduler;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    @PostMapping("/ohlcv/collect")
    @Operation(summary = "[개발용] OHLCV 수집 수동 트리거")
    public ResponseEntity<String> triggerOhlcvCollect() {
        ohlcvCollectorScheduler.collectDailyOhlcv();
        return ResponseEntity.ok("OHLCV 수집 완료");
    }

    @PostMapping("/auth/token")
    @Operation(summary = "[개발용] 실제 소셜 로그인 없이 테스트 사용자 JWT 발급")
    public ResponseEntity<TokenResponse> issueDevToken() {
        OAuthUserInfo devUserInfo = new OAuthUserInfo(
            OAuthProvider.GOOGLE, DEV_TEST_PROVIDER_ID,
            "dev@test.local", "개발테스트유저", null);
        User user = userService.findOrCreate(devUserInfo);

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenStore.save(user.getId(), refreshToken);

        return ResponseEntity.ok(AuthMapper.toTokenResponse(
            accessToken, refreshToken, jwtTokenProvider.getAccessTokenValidity()));
    }
}
