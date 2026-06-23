package com.kingdom.Service.AiService;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.Period;

import java.util.List;

/**
 * Shared prompt builder so EVERY kingdom AI starts from the SAME base prompt. Only the kingdom-specific
 * bits change (its name, how a finish is verified, and which metricKey it may use). This keeps all the
 * AIs consistent and easy for the team to maintain in one place.
 *
 * What the AI is NOT allowed to decide (so the numbers stay fair and consistent):
 *  - difficulty and period -> the app chooses them and passes them in.
 *  - XP -> fixed per (difficulty, period); see {@link ChallengeXp}.
 */
public final class ChallengePrompts {

    private ChallengePrompts() {
        // utility class — not meant to be instantiated
    }

    /**
     * Build the full instructions text for one kingdom AI.
     *
     * @param kingdomName        e.g. "FITNESS" (just for the wording)
     * @param verificationLine   1-2 sentences telling the model how a finish is verified for this kingdom
     * @param verificationSource the fixed source string to echo back, e.g. "GOOGLE_HEALTH"
     * @param metricKeyOptions   what the "metricKey" field may be, e.g. one of "steps", "calories" ...
     * @param difficulty         EASY / MEDIUM / HARD (decided by the app from the player's division)
     * @param period             DAILY / WEEKLY / MONTHLY / YEARLY
     * @param existingChallenges already-used challenges to avoid repeating (may be null/empty)
     */
    public static String build(String kingdomName,
                               String verificationLine,
                               String verificationSource,
                               String metricKeyOptions,
                               Difficulty difficulty,
                               Period period,
                               List<String> existingChallenges) {

        String existing = (existingChallenges == null || existingChallenges.isEmpty())
                ? "(none yet)"
                : String.join("; ", existingChallenges);

        return """
                You generate ONE %s challenge for a gamified app.
                %s

                This challenge's DIFFICULTY is %s and its PERIOD is %s — make the target match
                (a harder difficulty or a longer period means a bigger, more demanding target).
                Do NOT repeat or closely copy any of these existing challenges: %s.

                Output ONLY a strict JSON object with EXACTLY these fields
                (do NOT add an "xpReward" field — the app sets the XP itself):
                  "title": short and motivating, IN ARABIC (max ~40 characters)
                  "description": 1-2 clear sentences IN ARABIC stating the exact metric and target
                  "verificationSource": "%s"
                  "metricKey": %s
                  "targetValue": a whole number the player must reach
                Write "title" and "description" in ARABIC (فصحى). Keep "verificationSource",
                "metricKey" and "targetValue" EXACTLY as given above (do NOT translate those).
                Return ONLY the JSON object — no markdown, no comments.
                """.formatted(kingdomName, verificationLine, difficulty, period,
                              existing, verificationSource, metricKeyOptions);
    }
}
