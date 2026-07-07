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
     * 프론트엔드(Vite 개발 서버, 기본 포트 3000)가 별도 오리진에서
     * /api/**를 호출할 수 있도록 허용한다. 토큰은 쿠키가 아니라
     * Authorization 헤더로 실어 보내므로 자격증명 공유(allowCredentials)는
     * 불필요 - 와일드카드 오리진과의 충돌도 피할 수 있다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);
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
