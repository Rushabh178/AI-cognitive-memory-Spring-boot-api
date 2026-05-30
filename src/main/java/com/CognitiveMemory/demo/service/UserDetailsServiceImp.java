package com.CognitiveMemory.demo.service;

import com.CognitiveMemory.demo.Repository.UserRepository;
import com.CognitiveMemory.demo.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImp implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String input) throws UsernameNotFoundException {
        User user = userRepository.findByUserNameOrEmail(input, input);
        if (user == null) {
            throw new UsernameNotFoundException("User not found with username or email: " + input);
        }

        org.springframework.security.core.userdetails.UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUserName())
                .password(user.getPassword())
                .roles("USER")
                .build();

        return userDetails;
    }


}
