package com.quantlime.auth.jwt;

import com.quantlime.auth.exception.AuthErrorCode;
import com.quantlime.common.exception.UnauthorizedException;
import com.quantlime.user.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;

    // 요청마다 Keys.hmacShaKeyFor(...)로 새로 유도하지 않고 생성자에서 한
    // 번만 계산해 재사용한다(2026-08-19 - JWT 인증이 모든 요청의 스테이트리스
    // 검증 경로라 이 비용이 요청 수만큼 반복됐다). @PostConstruct가 아니라
    // 생성자에서 계산하는 이유: 이 클래스는 기존 테스트가 스프링 컨테이너
    // 없이 `new JwtTokenProvider(jwtProperties)`로 직접 생성해 쓰는데,
    // @PostConstruct는 스프링이 빈을 만들 때만 호출되고 직접 new할 땐
    // 실행되지 않는다.
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, UserRole role) {
        return createToken(userId, TYPE_ACCESS, role, jwtProperties.getAccessTokenValidity());
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, TYPE_REFRESH, null, jwtProperties.getRefreshTokenValidity());
    }

    public Long validateAndGetUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public UserRole getRole(String token) {
        String role = parseClaims(token).get(CLAIM_ROLE, String.class);
        return role != null ? UserRole.valueOf(role) : null;
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(parseClaims(token).get(CLAIM_TYPE, String.class));
    }

    /**
     * 위 3개 메서드를 개별 호출하면 요청 1건당 서명 검증(HMAC)과 JSON 파싱이
     * 3번 반복된다({@link com.quantlime.auth.filter.JwtAuthenticationFilter}가
     * 매 인증 요청마다 이 조합을 그대로 호출하던 게 이 프로젝트에서 가장
     * 트래픽이 큰 호출부다) - 필터처럼 한 요청에서 토큰 정보 전체가 필요한
     * 호출부는 이 메서드로 한 번만 파싱한다. 기존 3개 메서드는 다른
     * 호출부(재발급 등 저빈도 경로, 기존 테스트) 호환을 위해 그대로 둔다.
     */
    public ParsedToken parseToken(String token) {
        Claims claims = parseClaims(token);
        String roleValue = claims.get(CLAIM_ROLE, String.class);
        return new ParsedToken(
            Long.valueOf(claims.getSubject()),
            roleValue != null ? UserRole.valueOf(roleValue) : null,
            TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))
        );
    }

    public record ParsedToken(Long userId, UserRole role, boolean isRefreshToken) {
    }

    public long getAccessTokenValidity() {
        return jwtProperties.getAccessTokenValidity();
    }

    public long getRefreshTokenValidity() {
        return jwtProperties.getRefreshTokenValidity();
    }

    private String createToken(Long userId, String type, UserRole role, long validityMs) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
            .subject(String.valueOf(userId))
            .claim(CLAIM_TYPE, type)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(validityMs)))
            .signWith(secretKey);
        if (role != null) {
            builder.claim(CLAIM_ROLE, role.name());
        }
        return builder.compact();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException(AuthErrorCode.INVALID_TOKEN);
        }
    }
}
