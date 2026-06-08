package com.CognitiveMemory.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatBody {

    @NotBlank(message = "message must not be blank")
    private String message;

    @NotNull(message = "sessionId must not be null")
    private Long sessionId;
}
