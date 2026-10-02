package com.CognitiveMemory.demo.sessions.Repository;

import com.CognitiveMemory.demo.sessions.entity.ChatMessage;
import com.CognitiveMemory.demo.sessions.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop50BySessionOrderByCreatedAtDesc(ChatSession session);
    // Used to build the sessionHistory sent to the LLM (see SessionController.postUserMessage) —
    // deliberately a small, separate cap from findTop50 above (used for frontend display),
    // since every extra turn here is extra tokens in every future chat request for this session.
    List<ChatMessage> findTop10BySessionOrderByCreatedAtDesc(ChatSession session);
    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ChatSession session);
    long countBySession(ChatSession session);
    // Derived delete queries load then remove entities, which requires an active
    // transaction — without this, callers get TransactionRequiredException.
    @Transactional
    void deleteBySession(ChatSession session);
}

