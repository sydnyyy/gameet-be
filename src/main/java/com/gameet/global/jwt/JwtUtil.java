package com.gameet.global.jwt;

import com.gameet.global.exception.JwtAuthenticationException;
import com.gameet.user.enums.Role;
import com.gameet.global.exception.ErrorCode;
import io.jsonwebtoken.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtUtil {

    private static final long ACCESS_TOKEN_EXPIRATION = 1000 * 60 * 15;
    private static final long REFRESH_TOKEN_EXPIRATION = 1000 * 60 * 60 * 24 * 7;
    private static final long WEBSOCKET_TOKEN_EXPIRATION = 1000 * 10;
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    public final static String COOKIE_REFRESH_TOKEN_NAME = "refresh_token";

    private final JwtProperties jwtProperties;

    public String generateAccessToken(Long userId, Role role) {
        return generateToken(userId, role, ACCESS_TOKEN_EXPIRATION);
    }

    public String generateRefreshToken(Long userId, Role role) {
        return generateToken(userId, role, REFRESH_TOKEN_EXPIRATION);
    }

    public String generateWebSocketToken(Long userId, Role role) {
        return generateToken(userId, role, WEBSOCKET_TOKEN_EXPIRATION);
    }

    private String generateToken(Long userId, Role role, long expirationTime) {
        if (userId == null) {
            throw new JwtAuthenticationException(ErrorCode.USER_ID_REQUIRED);
        }

        if (role == null) {
            throw new JwtAuthenticationException(ErrorCode.ROLE_REQUIRED);
        }

        return Jwts.builder()
                .setIssuer(jwtProperties.getIssuer())
                .setSubject(userId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .claim("userId", userId)
                .claim("role", role.name())
                .signWith(SignatureAlgorithm.HS256, jwtProperties.getSecretKey())
                .compact();
    }

    public String getAccessToken(HttpServletRequest httpServletRequest) {
        String header = httpServletRequest.getHeader(HEADER_AUTHORIZATION);
        if (header != null && header.startsWith(TOKEN_PREFIX)) {
            return header.split(" ", 2)[1];
        }
        return null;
    }

    public String getAccessTokenFromAccessor(StompHeaderAccessor stompHeaderAccessor) {
        String header = stompHeaderAccessor.getFirstNativeHeader(HEADER_AUTHORIZATION);
        if (header != null && header.startsWith(TOKEN_PREFIX)) {
            return header.split(" ", 2)[1];
        }
        return null;
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.get("userId", Long.class);
        } catch (ExpiredJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException e) {
            throw new JwtAuthenticationException(ErrorCode.JWT_PROCESSING_FAILED);
        }
    }

    public Role getRoleFromToken(String token) {
        try {
            Claims claims = getClaims(token);
            String roleString = claims.get("role", String.class);
            return Role.from(roleString);
        } catch (ExpiredJwtException e) {
            throw new JwtAuthenticationException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException e) {
            throw new JwtAuthenticationException(ErrorCode.JWT_PROCESSING_FAILED);
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .setSigningKey(jwtProperties.getSecretKey())
                .parseClaimsJws(token)
                .getBody();
    }

    public Claims getClaimsAllowExpired(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(jwtProperties.getSecretKey())
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.warn("[getClaimsAllowExpired] extracting claims from expired token={}", e.getMessage());
            return e.getClaims();
        }
    }

    public String getRefreshToken(HttpServletRequest httpServletRequest) {
        Cookie[] cookies = httpServletRequest.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(COOKIE_REFRESH_TOKEN_NAME)) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
