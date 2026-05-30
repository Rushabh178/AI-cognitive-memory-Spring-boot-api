package com.CognitiveMemory.demo.Repository;

import com.CognitiveMemory.demo.entity.RefreshToken;
import com.CognitiveMemory.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Integer> {

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUser(User user);

    void deleteByUser(User user);
}
