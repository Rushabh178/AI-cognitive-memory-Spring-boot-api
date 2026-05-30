package com.CognitiveMemory.demo.service;

import com.CognitiveMemory.demo.Repository.UserRepository;
import com.CognitiveMemory.demo.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public boolean saveNewEntry(User user) {
        try {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            user.setRoles(Arrays.asList("USER"));
            userRepository.save(user);
            return true;
        } catch (Exception e) {
            log.error("Error saving new user: {}", user.getUserName(), e);
            return false;
        }
    }

    public boolean changePassword(User user, String newPassword) {
        if (user == null || newPassword == null || newPassword.isEmpty()) return false;
        try {
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            return true;
        } catch (Exception e) {
            log.error("Failed to change password for user {}", user.getUserName(), e);
            return false;
        }
    }

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public void saveAdmin(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRoles(Arrays.asList("USER", "ADMIN"));
        userRepository.save(user);
    }

    public User findByUsernameOrEmail(String usernameOrEmail) {
        User user = userRepository.findByEmail(usernameOrEmail);
        if (user == null) {
            user = userRepository.findByUserName(usernameOrEmail);
        }
        return user;
    }


}
