package org.example.travel_agent.service;

import org.example.travel_agent.dao.entity.UserEntity;

import java.util.Optional;

public interface UserService {

    Optional<UserEntity> getByUsername(String username);

    Optional<UserEntity> getByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    void saveUser(UserEntity user);
}
