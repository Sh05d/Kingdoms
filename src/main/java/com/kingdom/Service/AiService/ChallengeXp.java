package com.kingdom.Service.AiService;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.Period;

/**
 * Fixed XP for a challenge, based on its difficulty and period.
 *
 * Why fixed (and not AI-generated): when the AI invented the XP it gave inconsistent, unfair numbers.
 * A fixed table means the same difficulty + period ALWAYS award the same XP. When a challenge is created
 * from the AI output, set the reward with: challenge.setXpReward(ChallengeXp.xpFor(difficulty, period)).
 *
 * Table:
 *                 EASY   MEDIUM  HARD
 *   DAILY          50      80     120
 *   WEEKLY        100     140     180
 *   MONTHLY       200     250     300
 */
public final class ChallengeXp {

    private ChallengeXp() {
        // utility class — not meant to be instantiated
    }

    public static int xpFor(Difficulty difficulty, Period period) {
        // Fail loudly on bad input instead of silently mis-pricing XP — XP must stay fair and consistent.
        if (difficulty == null || period == null) {
            throw new IllegalArgumentException("difficulty and period are required to price a challenge's XP");
        }
        switch (period) {
            case DAILY:   return byDifficulty(difficulty, 50, 80, 120);
            case WEEKLY:  return byDifficulty(difficulty, 100, 140, 180);
            case MONTHLY: return byDifficulty(difficulty, 200, 250, 300);
            // If a new Period is ever added, fail here so we notice and price it (instead of giving wrong XP).
            default:      throw new IllegalArgumentException("No XP defined for period: " + period);
        }
    }

    // Pick the easy / medium / hard value for the given difficulty.
    private static int byDifficulty(Difficulty difficulty, int easy, int medium, int hard) {
        switch (difficulty) {
            case EASY:   return easy;
            case MEDIUM: return medium;
            case HARD:   return hard;
            // If a new Difficulty is ever added, fail here so we notice and price it.
            default:     throw new IllegalArgumentException("No XP defined for difficulty: " + difficulty);
        }
    }
}
