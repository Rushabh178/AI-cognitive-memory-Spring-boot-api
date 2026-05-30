package com.CognitiveMemory.demo.sessions.service;

import com.CognitiveMemory.demo.Repository.UserRepository;
import com.CognitiveMemory.demo.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new RuntimeException("Unauthenticated");
        }
        User user = userRepository.findByUserName(auth.getName());
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        return user;
    }
}

