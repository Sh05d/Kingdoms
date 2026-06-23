package com.kingdom.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class ChallengeQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "question is required")
    @Column(nullable = false)
    private String question;

    @NotBlank(message = "optionA is required")
    private String optionA;

    @NotBlank(message = "optionB is required")
    private String optionB;

    @NotBlank(message = "optionC is required")
    private String optionC;

    @NotBlank(message = "optionD is required")
    private String optionD;

    @NotBlank(message = "correctAnswer is required")
    @Column(nullable = false)
    private String correctAnswer;

    @NotNull(message = "challenge is required")
    @ManyToOne
    @JsonIgnore
    private Challenge challenge;
}
