package org.example.travel_agent.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.config.JwtProperties;
import org.example.travel_agent.dao.entity.UserEntity;
import org.example.travel_agent.dto.UserInfo;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;

    public String createAccessToken(UserEntity userEntity) {
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expiresAt = new Date(now + jwtProperties.getExpirationMs());
        return Jwts.builder()
                .subject(userEntity.getId().toString())
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .claims(
                        Map.of(
                                "username",userEntity.getUsername(),
                                "email", userEntity.getEmail() == null ? "" : userEntity.getEmail(),
                                "isAdmin", userEntity.getIsAdmin() == null ? 0 : userEntity.getIsAdmin()
                        )
                )
                .signWith(signingKey())
                .compact();
    }

    public UserInfo parseUserInfo(String token) {
        try {
            Claims payload = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String username = payload.get("username", String.class);
            String email = payload.get("email", String.class);
            Integer isAdmin = payload.get("isAdmin", Integer.class);
            String userId = payload.getSubject();
            if (isAdmin == null) {
                isAdmin = 0;
            }
            return UserInfo.builder()
                    .id(Long.parseLong(userId))
                    .username(username)
                    .email(email)
                    .isAdmin(isAdmin)
                    .build();

        } catch (ExpiredJwtException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw e;
        }
    }

    private SecretKey signingKey() {
        byte[] bytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(bytes);
    }
}
