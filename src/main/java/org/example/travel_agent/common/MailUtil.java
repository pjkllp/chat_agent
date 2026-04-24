package org.example.travel_agent.common;

import cn.hutool.core.util.RandomUtil;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.dto.auth.RegisterRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MailUtil{

    private final JavaMailSender mailSender;

    private final StringRedisTemplate stringRedisTemplate;

    public static final String USER_REGISTER_CODE_KEY="user:register:code:%s";

    public static final String USER_REVISE_CODE_KEY="user:revise:code:%s";

    public String sendEmail(RegisterRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("2797857024@qq.com");
        //邮箱接收者
        message.setTo(request.getEmail());
        //邮箱主题
        String subject = "验证码";
        message.setSubject(subject);
        String code = RandomUtil.randomNumbers(6);
        //邮箱内容
        String text = "欢迎注册彭先生智能体的用户，你的验证码是：" +code+"，请妥善保存，在三分钟之内填写";
        message.setText(text);
        mailSender.send(message);
        stringRedisTemplate.opsForValue().set(String.format(USER_REGISTER_CODE_KEY,request.getEmail()),code,3, TimeUnit.MINUTES);
        return code;
    }

    public void sendReviseMail(String mail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("2797857024@qq.com");
        //邮箱接收者
        message.setTo(mail);
        //邮箱主题
        String subject = "验证码";
        message.setSubject(subject);
        String code = RandomUtil.randomNumbers(6);
        //邮箱内容
        String text = "您正在修改密码，如果不是本人操作请忽略，并且提高防护，你的验证码是：" +code+"，请妥善保存，在三分钟之内填写";
        message.setText(text);
        mailSender.send(message);
        stringRedisTemplate.opsForValue().set(String.format(USER_REVISE_CODE_KEY,mail),code,3, TimeUnit.MINUTES);
    }
}
