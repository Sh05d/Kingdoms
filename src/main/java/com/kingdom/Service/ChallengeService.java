package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.DTO.IN.ChallengeIn;
import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.KingdomType;
import com.kingdom.Enums.Period;
import com.kingdom.Model.Challenge;
import com.kingdom.Model.Kingdom;
import com.kingdom.Repository.ChallengeRepository;
import com.kingdom.Repository.KingdomRepository;
import com.kingdom.Service.AiService.KingdomAiRegistry;
import com.kingdom.Service.AiService.KingdomAiService;
import com.kingdom.Service.AiService.ChallengeXp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    // TEAMMATE (User/Kingdom flow owns Kingdom): used read-only to tie a challenge to its kingdom by id.
    private final KingdomRepository kingdomRepository;
    // Anas: picks the right per-kingdom AI (Fitness/Charity/Volunteer) by KingdomType to generate a challenge.
    private final KingdomAiRegistry kingdomAiRegistry;
    // Shahad: every AI service (her addChallenge(...) generator picks by kingdom()) + JSON/question support.
    private final List<KingdomAiService> kingdomAiServices;
    private final ObjectMapper objectMapper;
    private final ChallengeQuestionService challengeQuestionService;

    // Anas: pull fields out of the AI's JSON challenge (simple + tolerant of code-fences / extra text).
    private static final Pattern TITLE  = Pattern.compile("\"title\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern DESC   = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern VSRC   = Pattern.compile("\"verificationSource\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern METRIC = Pattern.compile("\"metricKey\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern TARGET = Pattern.compile("\"targetValue\"\\s*:\\s*(\\d+)");

    // verificationSource values a verifier actually handles at finish — anything else would auto-pass.
    private static final Set<String> VERIFIABLE_SOURCES =
            Set.of("STRAVA", "NEOTEK_OPEN_BANKING", "AI_PDF_MATCH", "STEAM");

    // The exact metricKeys the Fitness (Strava) verifier understands. A STRAVA challenge with any other
    // metricKey hits the verifier's switch default (sums nothing) -> total stays 0 < target -> never passes.
    private static final Set<String> STRAVA_METRICS =
            Set.of("distance_meters", "moving_time_seconds", "activity_count");

    public List<Challenge> getAllChallenges() {
        return challengeRepository.findAll();
    }

    public void addChallenge(Integer kingdomId, Challenge challenge) {

        Kingdom kingdom = findKingdomById(kingdomId);

        challenge.setKingdom(kingdom);

        if (challenge.getXpReward() == null) {
            challenge.setXpReward(
                    calculateXp(
                            challenge.getDifficulty(),
                            challenge.getPeriod()
                    )
            );
        }

        if (challenge.getStartDate() == null) {
            challenge.setStartDate(LocalDateTime.now());
        }

        if (challenge.getEndDate() == null) {

            LocalDateTime now = challenge.getStartDate();

            challenge.setEndDate(
                    switch (challenge.getPeriod()) {
                        case DAILY -> now.plusDays(1);
                        case WEEKLY -> now.plusWeeks(1);
                        case MONTHLY -> now.plusMonths(1);
                    }
            );
        }

        challengeRepository.save(challenge);
    }

    // Generate a challenge with the kingdom's AI, then save it. The AI decides the title / description /
    // verificationSource / metricKey / targetValue; the app sets difficulty + period (passed in) and the
    // fixed XP (ChallengeXp). Returns the saved challenge.
    public Challenge generateChallenge(Integer kingdomId, Difficulty difficulty, Period period) {
        Kingdom kingdom = kingdomRepository.findKingdomById(kingdomId);
        if (kingdom == null) {
            throw new ApiException("Kingdom not found");
        }
        // Everyone EXCEPT Anas's 3 kingdoms goes through the general AI generator (addChallenge), which sets
        // verificationSource + verificationRule + verificationTarget + targetName from the AI JSON:
        //   - Shahad: READING/FAITH need verificationSource=WHATSAPP + the 5 quiz questions; GAMING needs the
        //     verificationRule/verificationTarget that steamCheck reads.
        //   - Maysun: KNOWLEDGE/NUTRITION/PROGRAMMING verifiers read verificationSource + verificationRule
        //     (e.g. NUTRITION requires verificationRule=FOOD_IMAGE_ANALYSIS) which the numeric path below never sets.
        // My guard + the Strava metric/target checks stay for Sports/Charity/Volunteer only.
        KingdomType type = kingdom.getType();
        if (type != KingdomType.SPORTS && type != KingdomType.CHARITY && type != KingdomType.VOLUNTEERING) {
            return addChallenge(kingdomId, difficulty, period);
        }
        KingdomAiService ai = kingdomAiRegistry.forKingdom(kingdom.getType());
        if (ai == null) {
            throw new ApiException("No AI is implemented for kingdom type " + kingdom.getType());
        }

        // Existing challenge titles in this kingdom, so the AI avoids repeating them.
        List<String> existing = new ArrayList<>();
        for (Challenge c : challengeRepository.findAllByKingdom_Id(kingdomId)) {
            existing.add(c.getTitle());
        }

        String json = ai.generateChallenge(difficulty, period, existing);
        if (json == null || json.isBlank()) {
            throw new ApiException("AI is off/unconfigured, or it returned nothing");
        }

        // Validate the AI output so we NEVER save an un-verifiable challenge (which would auto-pass at finish):
        // the verificationSource must be one a verifier handles, and numeric sources (Strava/Neotek) need a
        // POSITIVE target (target <= 0 makes "total >= target" trivially true).
        String source = match(VSRC, json, null);
        Integer parsedTarget = parseTarget(match(TARGET, json, null));
        if (source == null || !VERIFIABLE_SOURCES.contains(source)) {
            throw new ApiException("AI returned an unverifiable challenge (verificationSource=" + source + ") — try again");
        }
        if (("STRAVA".equals(source) || "NEOTEK_OPEN_BANKING".equals(source))
                && (parsedTarget == null || parsedTarget <= 0)) {
            throw new ApiException("AI returned an unverifiable challenge (target must be > 0) — try again");
        }
        // A STRAVA challenge MUST use a metricKey the verifier handles, otherwise its finish can never pass
        // (the verifier sums nothing for an unknown metric, so total stays 0 and never reaches the target).
        String metricKey = match(METRIC, json, null);
        if ("STRAVA".equals(source) && (metricKey == null || !STRAVA_METRICS.contains(metricKey))) {
            throw new ApiException("AI returned an unverifiable challenge (metricKey '" + metricKey
                    + "' isn't a Strava metric) — try again");
        }

        Challenge challenge = new Challenge();
        challenge.setTitle(match(TITLE, json, "Untitled challenge"));
        challenge.setDescription(match(DESC, json, ""));
        challenge.setPeriod(period);
        challenge.setDifficulty(difficulty);
        challenge.setVerificationSource(source);
        challenge.setMetricKey(metricKey);
        challenge.setTargetValue(parsedTarget);
        challenge.setXpReward(ChallengeXp.xpFor(difficulty, period));
        challenge.setKingdom(kingdom);

        challengeRepository.save(challenge);
        return challenge;
    }

    public Challenge getChallengeById(Integer id) {
        Challenge challenge = challengeRepository.findChallengeById(id);
        if (challenge == null) {
            throw new ApiException("Challenge not found");
        }
        return challenge;
    }

    public void updateChallenge(Integer id, ChallengeIn in) {
        Challenge oldChallenge = getChallengeById(id);
        applyIn(oldChallenge, in);
        challengeRepository.save(oldChallenge);
    }

    public void deleteChallenge(Integer id) {
        Challenge challenge = getChallengeById(id);
        challengeRepository.delete(challenge);
    }

    // Copy the request fields onto a Challenge entity. Two things are NOT taken from the client:
    //  - xpReward -> set from the fixed table (ChallengeXp) so XP stays fair and consistent.
    //  - kingdom  -> resolved by id (teammate model, read-only) when a kingdomId is given.
    private void applyIn(Challenge challenge, ChallengeIn in) {
        challenge.setTitle(in.getTitle());
        challenge.setDescription(in.getDescription());
        challenge.setPeriod(in.getPeriod());
        challenge.setStartDate(in.getStartDate());
        challenge.setEndDate(in.getEndDate());
        challenge.setDifficulty(in.getDifficulty());
        challenge.setVerificationSource(in.getVerificationSource());
        challenge.setMetricKey(in.getMetricKey());
        challenge.setTargetValue(in.getTargetValue());

        // XP is fixed per (difficulty, period) — the client never sets it.
        challenge.setXpReward(ChallengeXp.xpFor(in.getDifficulty(), in.getPeriod()));

        // Tie the challenge to its kingdom when an id is given.
        if (in.getKingdomId() != null) {
            Kingdom kingdom = kingdomRepository.findKingdomById(in.getKingdomId());
            if (kingdom == null) {
                throw new ApiException("Kingdom not found");
            }
            challenge.setKingdom(kingdom);
        }
    }

    // Challenges in a kingdom whose active window is currently open.
    public List<Challenge> getChallengesByKingdom(Integer kingdomId) {
        return onlyActive(challengeRepository.findAllByKingdom_Id(kingdomId));
    }

    // Browse currently-active challenges by difficulty (EASY / MEDIUM / HARD).
    public List<Challenge> getChallengesByDifficulty(Difficulty difficulty) {
        return onlyActive(challengeRepository.findAllByDifficulty(difficulty));
    }

    // Browse currently-active challenges by period (DAILY / WEEKLY / MONTHLY).
    public List<Challenge> getChallengesByPeriod(Period period) {
        return onlyActive(challengeRepository.findAllByPeriod(period));
    }

    // Keep only challenges whose active window is currently open (null dates = always open).
    private List<Challenge> onlyActive(List<Challenge> challenges) {
        LocalDateTime now = LocalDateTime.now();
        List<Challenge> active = new ArrayList<>();
        for (Challenge challenge : challenges) {
            boolean started = challenge.getStartDate() == null || !challenge.getStartDate().isAfter(now);
            boolean notEnded = challenge.getEndDate() == null || !challenge.getEndDate().isBefore(now);
            if (started && notEnded) {
                active.add(challenge);
            }
        }
        return active;
    }

    // Return the first regex group from the AI text, or the fallback if not found.
    private String match(Pattern pattern, String text, String fallback) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : fallback;
    }

    // Parse the AI's targetValue safely: non-numeric or out-of-range -> null (don't 500 / don't overflow Integer).
    private Integer parseTarget(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            long value = Long.parseLong(raw);
            if (value < 0) {
                return 0;
            }
            return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Shahad (Reading/Gaming/Faith): AI-generate a challenge, parse JSON, save multiple-choice questions ----
    // @Transactional so the challenge + its 5 questions commit together: if saveChallengeQuestions throws, the
    // challenge insert rolls back too (no challenge left with zero/partial questions).
    @Transactional
    public Challenge addChallenge(Integer kingdomId, Difficulty difficulty, Period period) {
        Kingdom kingdom = findKingdomById(kingdomId);

        List<Challenge> existingChallenges =
                challengeRepository.findByKingdomAndEndDateAfter(kingdom, LocalDateTime.now());

        List<String> existingTitles =
                existingChallenges.stream()
                        .flatMap(challenge -> List.of(
                                challenge.getTitle(),
                                challenge.getTargetName(),
                                challenge.getVerificationTarget()
                        ).stream())
                        .filter(value -> value != null && !value.isBlank())
                        .toList();

        KingdomAiService service = kingdomAiServices.stream()
                .filter(s -> s.kingdom() == kingdom.getType())
                .findFirst()
                .orElseThrow(() -> new ApiException("No AI service registered for this kingdom"));

        String json = service.generateChallenge(difficulty, period, existingTitles);

        if (json == null || json.isBlank()) {
            throw new ApiException("AI is disabled or returned no response");
        }

        try {
            JsonNode node = objectMapper.readTree(json);

            Challenge challenge = new Challenge();

            challenge.setTitle(node.path("title").asText(""));
            challenge.setDescription(node.path("description").asText(""));
            challenge.setDifficulty(difficulty);
            challenge.setPeriod(period);
            challenge.setXpReward(calculateXp(difficulty, period));
            challenge.setTargetValue(node.path("targetValue").asInt(0));

            // Read defensively: the lean fitness/gaming/charity prompt (ChallengePrompts) omits targetName /
            // verificationRule / verificationTarget and instead carries metricKey, so missing fields must not NPE.
            challenge.setTargetName(node.path("targetName").asText(""));

            if (kingdom.getType() == KingdomType.READING || kingdom.getType() == KingdomType.FAITH
                    || kingdom.getType() == KingdomType.KNOWLEDGE) {
                // WhatsApp quiz kingdoms: the finish flow sends these questions over WhatsApp and grades them.
                challenge.setVerificationSource("WHATSAPP");
            } else {
                challenge.setVerificationSource(node.path("verificationSource").asText(""));
            }
            challenge.setVerificationRule(node.path("verificationRule").asText(""));
            challenge.setVerificationTarget(node.path("verificationTarget").asText(""));
            if (node.hasNonNull("metricKey")) {
                challenge.setMetricKey(node.get("metricKey").asText()); // Strava metric for fitness (distance/time/count)
            }

            challenge.setKingdom(kingdom);

            LocalDateTime now = LocalDateTime.now();
            challenge.setStartDate(now);

            challenge.setEndDate(switch (period) {
                case DAILY -> now.plusDays(1);
                case WEEKLY -> now.plusWeeks(1);
                case MONTHLY -> now.plusMonths(1);
                default -> throw new ApiException("Unsupported period: " + period);
            });

            Challenge savedChallenge = challengeRepository.save(challenge);

            if (kingdom.getType() == KingdomType.READING
                    || kingdom.getType() == KingdomType.FAITH
                    || kingdom.getType() == KingdomType.KNOWLEDGE) {
                challengeQuestionService.saveChallengeQuestions(node, savedChallenge);
            }
            return savedChallenge;

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Failed to parse AI response into a challenge: " + e.getMessage());
        }
    }

    public Kingdom findKingdomById(Integer id) {
        Kingdom kingdom = kingdomRepository.findKingdomById(id);
        if (kingdom == null) {
            throw new ApiException("Kingdom not found");
        }
        return kingdom;
    }

    private Integer calculateXp(Difficulty difficulty, Period period) {

        return switch (period) {

            case DAILY -> switch (difficulty) {
                case EASY -> 50;
                case MEDIUM -> 80;
                case HARD -> 120;
            };

            case WEEKLY -> switch (difficulty) {
                case EASY -> 100;
                case MEDIUM -> 140;
                case HARD -> 180;
            };

            case MONTHLY -> switch (difficulty) {
                case EASY -> 200;
                case MEDIUM -> 250;
                case HARD -> 300;
            };

            default -> throw new ApiException("Unsupported period");
        };
    }
}
