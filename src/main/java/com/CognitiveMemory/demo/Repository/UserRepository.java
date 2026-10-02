package com.CognitiveMemory.demo.Repository;

import com.CognitiveMemory.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, Integer> {
    User findByUserName(String userName);
    User findByEmail(String email);
    // Finds a user where either userName or email matches the provided values
    User findByUserNameOrEmail(String userName, String email);
    @Transactional
    void deleteByUserName(String userName);

}
