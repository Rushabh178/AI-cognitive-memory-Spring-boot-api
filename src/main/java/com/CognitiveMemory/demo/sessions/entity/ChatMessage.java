package com.CognitiveMemory.demo.sessions.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id", referencedColumnName = "id")
    private ChatSession session;

    @Column(nullable = false)
    private String role; // "user" | "assistant"

    @Column(nullable = false, length = 8000)
    private String content;

    @Column(nullable = false)
    private Instant createdAt;
}

