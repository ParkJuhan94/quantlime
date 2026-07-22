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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;

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
            .signWith(secretKey());
        if (role != null) {
            builder.claim(CLAIM_ROLE, role.name());
        }
        return builder.compact();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
