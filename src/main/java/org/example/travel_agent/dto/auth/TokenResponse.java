package org.example.travel_agent.dto.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresInMs) {
}
