package com.CognitiveMemory.demo.sessions.Repository;

import com.CognitiveMemory.demo.sessions.entity.ChatMessage;
import com.CognitiveMemory.demo.sessions.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop50BySessionOrderByCreatedAtDesc(ChatSession session);
}

