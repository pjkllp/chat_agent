package org.example.travel_agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.travel_agent.dao.entity.UserEntity;
import org.example.travel_agent.dao.mapper.UserMapper;
import org.example.travel_agent.service.UserService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    @Override
    public Optional<UserEntity> getByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                this.getOne(
                        Wrappers.<UserEntity>lambdaQuery()
                                .eq(UserEntity::getUsername, username)
                                .last("LIMIT 1"),
                        false
                )
        );
    }

    @Override
    public Optional<UserEntity> getByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                this.getOne(
                        Wrappers.<UserEntity>lambdaQuery()
                                .eq(UserEntity::getEmail, email)
                                .last("LIMIT 1"),
                        false
                )
        );
    }

    @Override
    public boolean existsByUsername(String username) {
        return getByUsername(username).isPresent();
    }

    @Override
    public boolean existsByEmail(String email) {
        return getByEmail(email).isPresent();
    }

    @Override
    public void saveUser(UserEntity user) {
        this.save(user);
    }
}
