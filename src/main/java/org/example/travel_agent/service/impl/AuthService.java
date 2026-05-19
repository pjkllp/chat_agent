package org.example.travel_agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.Exceptions.ServerException;
import org.example.travel_agent.common.JwtUtil;
import org.example.travel_agent.common.MailUtil;
import org.example.travel_agent.dao.entity.UserEntity;
import org.example.travel_agent.dao.mapper.UserMapper;
import org.example.travel_agent.dto.auth.LoginRequest;
import org.example.travel_agent.dto.auth.RegisterRequest;
import org.redisson.api.RBloomFilter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.example.travel_agent.common.MailUtil.USER_REGISTER_CODE_KEY;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");

    private static final int MIN_PASSWORD_LEN = 6;

    private final StringRedisTemplate stringRedisTemplate;

    private final JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final UserMapper userMapper;

    private final MailUtil mailUtil;

    private final RBloomFilter<String> usernameRBloomFilter;

    private final String USER_LOGIN_KEY="user:login:%s";

    public void applyCode(RegisterRequest registerRequest) throws ClientException {

        verifyAccount(registerRequest);

        mailUtil.sendEmail(registerRequest);

    }

    public void register(RegisterRequest registerRequest) throws ClientException {

        String code = registerRequest.getCode();

        if (!Objects.equals(code,stringRedisTemplate.opsForValue().get(String.format(USER_REGISTER_CODE_KEY,registerRequest.getEmail())))){
            throw new ClientException("验证码错误");
        }

        UserEntity userEntity = UserEntity.builder()
                .username(registerRequest.getUsername())
                .password(registerRequest.getPassword())
                .email(registerRequest.getEmail())
                .createTime(new Date())
                .build();

        int insert = userMapper.insert(userEntity);

        if (insert!=1){
            throw new ServerException("注册失败");
        }

        usernameRBloomFilter.add(userEntity.getUsername());

    }

    public String passwordLogin(LoginRequest loginRequest) throws ClientException {
        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();
        validateUsername(username);
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        UserEntity userEntity = userMapper.selectOne(
                Wrappers.lambdaQuery(UserEntity.class)
                        .eq(UserEntity::getUsername, username)
                        .eq(UserEntity::getPassword, password)
        );
        if (userEntity==null){
            throw new ClientException("账号或密码错误");
        }
        String token = jwtUtil.createAccessToken(userEntity);
        stringRedisTemplate.opsForValue().set(
                String.format(USER_LOGIN_KEY,loginRequest.getUsername()),token,
                24, TimeUnit.HOURS
        );
        return token;
    }

    public void logout(String username) throws ClientException {
        validateUsername(username);
        stringRedisTemplate.delete(String.format(USER_LOGIN_KEY, username));
    }

    private void validateUsername(String username) throws ClientException {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new ClientException("用户名须为 3–32 位字母、数字或下划线");
        }
    }

    private void verifyAccount(RegisterRequest registerRequest) throws ClientException {
        String username = registerRequest.getUsername();
        String password = registerRequest.getPassword();
        String email = registerRequest.getEmail();
        validateUsername(username);
        if (password == null || password.length() < MIN_PASSWORD_LEN) {
            throw new ClientException("密码长度至少 " + MIN_PASSWORD_LEN + " 位");
        }

        //校验username是否存在
        UserEntity userEntity = userMapper.selectOne(Wrappers.lambdaQuery(UserEntity.class)
                .eq(UserEntity::getUsername, username)
                .or().eq(UserEntity::getEmail,email)
        );

        if(userEntity!=null){
            throw new ClientException("用户名或邮箱已经存在已经存在");
        }
    }

    public Boolean verifyUsername(String username) {
        if (usernameRBloomFilter.contains(username)) {
            return true;
        }
        UserEntity user = userMapper.selectOne(
                Wrappers.lambdaQuery(UserEntity.class).eq(UserEntity::getUsername, username));
        if (user != null) {
            usernameRBloomFilter.add(username);
            return true;
        }
        return false;
    }

}
