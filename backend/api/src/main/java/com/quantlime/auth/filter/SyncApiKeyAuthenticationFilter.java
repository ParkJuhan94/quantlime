package com.quantlime.auth.filter;

import com.quantlime.infra.sync.SyncProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로컬↔운영 자막 동기화 전용 인증 경로(2026-09-15) - 사람이 브라우저에서
 * 로그인해 JWT를 매번 복사해오는 수동 단계 없이, 로컬의 Kafka 컨슈머
 * (LocalTranscriptSyncConsumer)와 배치 스크립트(sync-transcripts-to-prod.sh)가
 * 반복 호출할 수 있어야 하기 때문에 별도로 둔다. 대상 경로 하나로만
 * 좁혀서(다른 /api/admin/** 는 여전히 JWT+ROLE_ADMIN만 허용) 이 대칭키가
 * 유출돼도 영향 범위를 최소화한다.
 *
 * <p>{@code sync.api-key}가 설정 안 돼 있으면(빈 문자열) 이 필터는 항상
 * 아무 일도 하지 않는다 - 즉 키를 안 채운 환경(로컬 기본값 등)에서는 이
 * 경로가 사실상 비활성화되고, 요청은 그대로 JwtAuthenticationFilter로
 * 넘어가 원래대로 401/403 처리된다.
 */
@Component
@RequiredArgsConstructor
public class SyncApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Sync-Api-Key";
    private static final String PROTECTED_PATH = "/api/admin/feed/transcripts/import";

    private final SyncProperties syncProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String configuredKey = syncProperties.getApiKey();
        if (StringUtils.hasText(configuredKey) && PROTECTED_PATH.equals(request.getRequestURI())) {
            String providedKey = request.getHeader(API_KEY_HEADER);
            if (StringUtils.hasText(providedKey) && MessageDigest.isEqual(
                providedKey.getBytes(StandardCharsets.UTF_8), configuredKey.getBytes(StandardCharsets.UTF_8))) {
                var authentication = new UsernamePasswordAuthenticationToken(
                    "local-sync", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
