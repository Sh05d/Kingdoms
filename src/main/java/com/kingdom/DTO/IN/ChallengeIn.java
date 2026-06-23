package com.kingdom.DTO.IN;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.Period;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Request body to create / update a Challenge.
 *
 * NOTE: there is NO xpReward field on purpose. XP is fixed per (difficulty, period) and the service sets
 * it from {@code ChallengeXp} — the client only chooses the difficulty and period.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeIn {

    @NotBlank(message = "title is required")
    private String title;

    @NotBlank(message = "description is required")
    private String description;

    @NotNull(message = "period is required")
    private Period period;

    private LocalDateTime startDate;   // optional (null = always open / no start)
    private LocalDateTime endDate;     // optional (null = never ends)

    @NotNull(message = "difficulty is required")
    private Difficulty difficulty;

    @NotBlank(message = "verificationSource is required")
    private String verificationSource;

    private String metricKey;          // optional: what is measured, e.g. "steps"
    private Integer targetValue;       // optional: the number the player must reach

    private Integer kingdomId;         // which kingdom this challenge belongs to (recommended)
}
