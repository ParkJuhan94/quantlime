package com.quantlab.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlab.auth.exception.AuthErrorCode;
import com.quantlab.auth.filter.JwtAuthenticationFilter;
import com.quantlab.common.exception.ErrorResponseTemplate;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
        "/dev/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/ws/**"
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
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
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
