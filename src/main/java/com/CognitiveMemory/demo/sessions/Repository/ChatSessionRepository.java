package com.CognitiveMemory.demo.sessions.Repository;

import com.CognitiveMemory.demo.entity.User;
import com.CognitiveMemory.demo.sessions.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByUserOrderByCreatedAtDesc(User user);
}

