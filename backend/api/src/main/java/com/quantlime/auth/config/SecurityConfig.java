package com.quantlime.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.auth.exception.AuthErrorCode;
import com.quantlime.auth.filter.JwtAuthenticationFilter;
import com.quantlime.common.exception.ErrorResponseTemplate;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PERMIT_ALL_PATTERNS = {
        "/api/auth/**",
        "/api/health",
        "/api/stocks/**",
        "/api/market/**",
        // 백테스트 결과(Rank IC/버킷/안정성)는 사용자별 데이터가 아니라
        // /api/stocks/**의 스코어 조회와 동일하게 누구나 볼 수 있는 공개
        // 분석 데이터다.
        "/api/backtest/**",
        "/api/feedback",
        // 토스페이먼츠가 서버 대 서버로 호출하는 웹훅 - 사용자 인증(JWT)이
        // 아니라 PaymentService.handleWebhook 내부의 서명 검증으로 대체한다.
        "/api/webhooks/**",
        // 업로드된 이미지 열람은 인증 불필요 - Slack 언퍼널링, 비로그인
        // 피드 열람 등에서 그대로 불러와야 한다. 업로드(POST)는 별개로
        // UploadController에서 @LoginUser로 막는다.
        "/uploads/**",
        "/dev/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/ws/**",
        // Prometheus 스크랩 대상(/actuator/prometheus) + health. nginx가
        // /actuator 경로를 프록시하지 않아(frontend/nginx.conf) 외부에서는
        // 애초에 도달 불가하고, docker 내부망의 Prometheus만 호출한다.
        "/actuator/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origin:http://localhost:3001}")
    private String allowedOrigin;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PERMIT_ALL_PATTERNS).permitAll()
                // 피드 글쓰기/좋아요/댓글 작성은 로그인이 필요하지만 조회는
                // 누구나 볼 수 있어야 해서 GET만 따로 permitAll한다(경로
                // 전체를 열면 글쓰기까지 비로그인으로 뚫림).
                .requestMatchers(HttpMethod.GET, "/api/feed/posts").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/feed/posts/*/comments").permitAll()
                // 실시간 랭킹 "스코어" 탭의 "전체" 토글(watchlistOnly=false)은
                // 관심종목과 무관한 공개 데이터라 로그인 없이도 조회 가능해야
                // 한다 - watchlistOnly=true(관심종목만)는 컨트롤러에서
                // @OptionalLoginUser로 비로그인 시 빈 배열을 반환해 처리
                // (2026-07-18, /api/market/ranking의 watchlistOnly와 동일한 패턴).
                .requestMatchers(HttpMethod.GET, "/api/dashboard/scores").permitAll()
                // 판매중인 구독 플랜 목록은 로그인 전에도(가입 유도 목적) 볼 수
                // 있어야 한다 - 구독 상태/결제/해지 등 나머지 /api/subscription/**
                // 는 여전히 인증 필요(anyRequest().authenticated()로 커버).
                .requestMatchers(HttpMethod.GET, "/api/subscription/plans").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint((request, response, e) ->
                    writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                        AuthErrorCode.INVALID_TOKEN))
                .accessDeniedHandler((request, response, e) ->
                    writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                        AuthErrorCode.INVALID_TOKEN)))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 프론트엔드(Vite 개발 서버, 기본 포트 3001)가 별도 오리진에서
     * /api/**·/ws/**를 호출할 수 있도록 허용한다.
     *
     * <p>allowCredentials(true)가 필요한 이유: REST 인증 자체는 쿠키가
     * 아니라 Authorization 헤더를 쓰지만, SockJS 클라이언트의 XHR
     * 폴백 트랜스포트(/ws/stocks/info 등)가 기본적으로
     * withCredentials=true로 요청을 보낸다. 이 경우 브라우저는 응답에
     * Access-Control-Allow-Credentials: true가 없으면 오리진이 일치해도
     * 응답 자체를 차단한다. 오리진을 특정 값 하나로 고정해뒀으므로
     * (와일드카드가 아님) allowCredentials(true)와 조합해도 안전하다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        // PATCH는 관심 그룹 이름 변경/종목 그룹 이동 API에서 새로 쓰기
        // 시작하면서 추가 - 빠져 있으면 브라우저 프리플라이트(OPTIONS)가
        // 거부돼 CORS 에러로만 보이고 서버 로그엔 아무 단서도 안 남는다.
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeErrorResponse(HttpServletResponse response, int status,
                                    AuthErrorCode errorCode) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
            new ErrorResponseTemplate(errorCode.getMessage(), errorCode.getCode())));
    }
}
