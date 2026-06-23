package com.kingdom.Service.AiService;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.KingdomType;
import com.kingdom.Enums.Period;

import java.util.List;

/**
 * Contract for a per-kingdom AI. Add ONE implementation per kingdom (9 total) in this package,
 * each a {@code @Service} that uses {@link OpenAiClient} to generate (and later verify) challenges
 * for its {@link KingdomType}. See README.md in this package for the list.
 */
public interface KingdomAiService {

    /** Which kingdom this AI serves. */
    KingdomType kingdom();

    /**
     * Generate ONE new challenge for this kingdom.
     *
     * @param difficulty         how hard it should be. The app decides this from the player's division
     *                           (division 1 -> HARD, 2 -> MEDIUM, 3 -> EASY) and passes it in.
     * @param period             the time window: DAILY / WEEKLY / MONTHLY / YEARLY.
     * @param existingChallenges challenges already in use, so the AI avoids repeating them (may be empty).
     * @return the model's text (a JSON challenge), or null if AI is off (the caller then falls back).
     *
     * NOTE: the AI does NOT decide the XP. XP is fixed per (difficulty, period) — see {@link ChallengeXp} —
     * because letting the model invent the number gave inconsistent, unfair values.
     */
    String generateChallenge(Difficulty difficulty, Period period, List<String> existingChallenges);
}
