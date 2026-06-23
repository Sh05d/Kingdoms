package com.kingdom.DTO.IN;


import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeAnswerIn {

    @NotNull(message = "questionId is required")
    private Integer questionId;

    @NotEmpty(message = "selectedAnswer is required")
    @Pattern(regexp = "^[ABCD]$", message = "selectedAnswer must be A, B, C, or D")
    private String selectedAnswer;
}
