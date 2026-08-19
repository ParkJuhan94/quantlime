package com.quantlime.auth.filter;

import com.quantlime.auth.exception.AuthErrorCode;
import com.quantlime.auth.jwt.JwtTokenProvider;
import com.quantlime.auth.jwt.JwtTokenProvider.ParsedToken;
import com.quantlime.common.exception.UnauthorizedException;
import com.quantlime.user.domain.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                // parseToken 한 번으로 서명 검증+파싱을 1회만 수행한다(이전엔
                // isRefreshToken/validateAndGetUserId/getRole 3개를 각각 호출해
                // 요청마다 서명 검증이 3번 반복됐다, 2026-08-19).
                ParsedToken parsedToken = jwtTokenProvider.parseToken(token);
                // 리프레시 토큰은 재발급(/api/auth/reissue) 전용이다 - 여기서
                // 걸러두지 않으면 리프레시 토큰을 액세스 토큰처럼 Authorization
                // 헤더에 실어 보내도 그대로 인증돼버린다(리프레시 토큰은 수명이
                // 14일로 훨씬 길어 탈취 시 피해 범위가 커짐).
                if (parsedToken.isRefreshToken()) {
                    throw new UnauthorizedException(AuthErrorCode.INVALID_TOKEN);
                }
                UserRole role = parsedToken.role();
                List<GrantedAuthority> authorities = role != null
                    ? List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
                    : List.of();
                var authentication = new UsernamePasswordAuthenticationToken(
                    parsedToken.userId(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (UnauthorizedException e) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
