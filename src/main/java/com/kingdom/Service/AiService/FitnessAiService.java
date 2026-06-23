package com.kingdom.Service.AiService;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.KingdomType;
import com.kingdom.Enums.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Generates FITNESS (SPORTS) challenges via the shared {@link OpenAiClient}.
 * Uses the shared {@link ChallengePrompts} base prompt — only the Fitness-specific bits differ.
 * Verified by Strava activity data (see com.kingdom.verification.FitnessVerificationService).
 */
@Service
@RequiredArgsConstructor
public class FitnessAiService implements KingdomAiService {

    private final OpenAiClient openAiClient;

    @Override
    public KingdomType kingdom() {
        return KingdomType.SPORTS;
    }

    @Override
    public String generateChallenge(Difficulty difficulty, Period period, List<String> existingChallenges) {
        String instructions = ChallengePrompts.build(
                "FITNESS",
                "Completion is verified from the player's Strava activities (runs, rides, walks, workouts), so "
                        + "the challenge must be something Strava records: a total distance, a total active time, "
                        + "or a number of activities — NOT a passive daily step count.",
                "STRAVA",
                "one of \"distance_meters\" (sum of activity distance), \"moving_time_seconds\" (sum of active "
                        + "time), or \"activity_count\" (number of activities)",
                difficulty, period, existingChallenges);
        return openAiClient.generate(instructions, "Generate one new challenge now.");
    }
}
