package org.example.travel_agent.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterRequest{

    private String username;

    private String password;

    private  String email;

    private String code;
}
