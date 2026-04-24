package org.example.travel_agent.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;

@Builder
@Data
public class UserInfo{

    private long id;

    private String username;

    private String email;

    private Integer isAdmin;


}
