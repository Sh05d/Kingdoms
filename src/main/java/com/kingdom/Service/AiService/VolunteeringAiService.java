package com.kingdom.Service.AiService;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.KingdomType;
import com.kingdom.Enums.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Generates VOLUNTEERING challenges via the shared {@link OpenAiClient}.
 * Uses the shared {@link ChallengePrompts} base prompt — only the Volunteer-specific bits differ.
 */
@Service
@RequiredArgsConstructor
public class VolunteeringAiService implements KingdomAiService {

    private final OpenAiClient openAiClient;

    @Override
    public KingdomType kingdom() {
        return KingdomType.VOLUNTEERING;
    }

    @Override
    public String generateChallenge(Difficulty difficulty, Period period, List<String> existingChallenges) {
        String instructions = ChallengePrompts.build(
                "VOLUNTEER",
                "Completion is verified by the player uploading a PDF proof (a volunteering certificate or "
                        + "official letter); an AI then compares it to trusted example certificates and approves it "
                        + "when the match score is high enough. So the challenge must end in a document the player "
                        + "can realistically obtain and upload.",
                "AI_PDF_MATCH",
                "\"volunteer_hours\"",
                difficulty, period, existingChallenges);
        return openAiClient.generate(instructions, "Generate one new challenge now.");
    }
}
