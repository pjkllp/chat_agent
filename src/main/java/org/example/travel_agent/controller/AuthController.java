package org.example.travel_agent.controller;

import cn.hutool.core.lang.func.VoidFunc;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.config.JwtProperties;
import org.example.travel_agent.dto.Result;
import org.example.travel_agent.dto.auth.LoginRequest;
import org.example.travel_agent.dto.auth.RegisterRequest;
import org.example.travel_agent.dto.auth.TokenResponse;
import org.example.travel_agent.service.impl.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RequestMapping("/api/auth")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;


    /**
     * 在用户注册的时候，先输入username,password和email，然后用户点击发送验证码，后端验证其是否符合要求
     * 如果符合要求就给用户qq邮箱发送验证码，然后用户输入完验证码后点击注册，这时就需要调用register接口
     * @param requestParam
     * @return
     * @throws ClientException
     */
    @PostMapping("/applyCode")
    public Result<Void> applyCode(@RequestBody RegisterRequest requestParam) throws ClientException {
        authService.applyCode(requestParam);
        return Result.success("验证码发送成功");
    }

    /**
     * 然后用户输入完验证码后点击注册，这时就需要调用register接口
     * @param requestParam
     * @return
     * @throws ClientException
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody RegisterRequest requestParam) throws ClientException {
          authService.register(requestParam);
          return Result.success("注册成功");
    }

    /**
     * 用户登录接口
     * @param body
     * @return
     * @throws ClientException
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginRequest body) throws ClientException {
        String token = authService.passwordLogin(body);
        return Result.success("登录成功",token);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestParam("username") String username) throws ClientException {
        authService.logout(username);
        return Result.success("退出登录成功");
    }

    /**
     * 在登录的时候，用户输入username完成的时候判断，是否存在该用户名，如果不存在就显示出来，让用户体验更好
     * @param username
     * @return
     */
    @GetMapping("/verify_username")
    public Result<Boolean> verifyUsername(@RequestParam("username")String username){
        return Result.success(authService.verifyUsername(username));
    }

}
