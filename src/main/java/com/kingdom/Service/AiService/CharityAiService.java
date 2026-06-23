package com.kingdom.Service.AiService;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.KingdomType;
import com.kingdom.Enums.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Generates CHARITY challenges via the shared {@link OpenAiClient}.
 * Uses the shared {@link ChallengePrompts} base prompt — only the Charity-specific bits differ.
 */
@Service
@RequiredArgsConstructor
public class CharityAiService implements KingdomAiService {

    private final OpenAiClient openAiClient;

    @Override
    public KingdomType kingdom() {
        return KingdomType.CHARITY;
    }

    @Override
    public String generateChallenge(Difficulty difficulty, Period period, List<String> existingChallenges) {
        String instructions = ChallengePrompts.build(
                "CHARITY",
                "Completion is verified by reading the player's bank transactions through Neotek Open Banking "
                        + "and confirming an OUTGOING donation to a registered charity (e.g. Ehsan / إحسان) of at "
                        + "least the target amount. So the challenge must be a real money donation in SAR — not a "
                        + "photo, not volunteering, not an in-kind gift. Keep amounts modest and inclusive.",
                "NEOTEK_OPEN_BANKING",
                "\"charity_donation_sar\"",
                difficulty, period, existingChallenges);
        return openAiClient.generate(instructions, "Generate one new challenge now.");
    }
}
