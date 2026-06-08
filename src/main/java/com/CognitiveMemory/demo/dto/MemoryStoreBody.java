package com.CognitiveMemory.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryStoreBody {

    @NotBlank(message = "content must not be blank")
    private String content;

    @NotBlank(message = "role must not be blank")
    private String role;
}
