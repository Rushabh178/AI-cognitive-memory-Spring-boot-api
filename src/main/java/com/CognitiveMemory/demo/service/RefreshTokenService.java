package com.CognitiveMemory.demo.service;

import com.CognitiveMemory.demo.Repository.RefreshTokenRepository;
import com.CognitiveMemory.demo.Repository.UserRepository;
import com.CognitiveMemory.demo.entity.RefreshToken;
import com.CognitiveMemory.demo.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    @Value("${jwt.refresh.expiration.ms}")
    private Long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    public RefreshToken createRefreshToken(Integer userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        Optional<RefreshToken> existingToken =
                refreshTokenRepository.findByUser(user);

        RefreshToken refreshToken;

        if (existingToken.isPresent()) {

            // Update existing token
            refreshToken = existingToken.get();

        } else {

            // Create new token
            refreshToken = new RefreshToken();
            refreshToken.setUser(user);
        }

        refreshToken.setToken(UUID.randomUUID().toString());

        refreshToken.setExpiryDate(
                Instant.now().plusMillis(refreshTokenDurationMs)
        );

        return refreshTokenRepository.save(refreshToken);
    }
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Transactional
    public RefreshToken verifyExpiration(RefreshToken token) {

        if (token.getExpiryDate().isBefore(Instant.now())) {

            refreshTokenRepository.delete(token);

            throw new RuntimeException(
                    "Refresh token expired. Please login again."
            );
        }

        return token;
    }

    public int deleteByUserId(Integer userId) {

        User user = userRepository.findById(userId)
                .orElseThrow();

        refreshTokenRepository.deleteByUser(user);

        return 1;
    }
}