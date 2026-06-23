# AI Service Package — `com.kingdom.Ai`

The AI layer for the Kingdom backend. Uses the **OpenAI Responses API** (model `gpt-5.5`),
mirroring the Wafferha pattern. Config is `openai.*` in `application.properties`; the real key lives
in the git-ignored `application-local.properties`.

## What's here (the scaffold)
- **`OpenAiClient`** — the shared engine. `generate(instructions, input)` → POSTs to `/v1/responses`
  with `gpt-5.5`. **Feature-guarded:** returns `null` when `openai.enabled=false` or no key, so every
  caller falls back gracefully (never throws).
- **`KingdomAiService`** — the interface each per-kingdom AI implements.
- **`KingdomSuggestionService`** — the suggestion AI ("+1").

## To add: the 9 per-kingdom AIs
Create one `@Service` per kingdom in this package, implementing `KingdomAiService` and using
`OpenAiClient`:

| Kingdom | KingdomType | Suggested class |
|---|---|---|
| Coding | LEARNING | `CodingAiService` |
| Games | GAMING | `GamesAiService` |
| Fitness | SPORTS | `FitnessAiService` |
| Charity | CHARITY | `CharityAiService` |
| Faith | FAITH | `FaithAiService` |
| Knowledge | CREATOR | `KnowledgeAiService` |
| Reading | READING | `ReadingAiService` |
| Nutrition | NUTRITION | `NutritionAiService` |
| Volunteering | VOLUNTEERING | `VolunteeringAiService` |

Tip: once the 9 exist, add a tiny registry so the verify step resolves the right one by kingdom:
```java
@Service
class KingdomAiRegistry {
    private final Map<KingdomType, KingdomAiService> byKingdom;
    KingdomAiRegistry(List<KingdomAiService> all) {
        byKingdom = all.stream().collect(Collectors.toMap(KingdomAiService::kingdom, s -> s));
    }
    KingdomAiService forKingdom(KingdomType type) { return byKingdom.get(type); }
}
```

## How a kingdom AI generates a challenge
All AIs share **one base prompt** (`ChallengePrompts.build(...)`) — only the kingdom-specific bits
(name, how it's verified, allowed `metricKey`) differ. The AI does **not** invent the XP, difficulty
or period:
- `difficulty` + `period` are decided by the app and passed **in** (difficulty comes from the player's
  division: 1 → HARD, 2 → MEDIUM, 3 → EASY).
- XP is **fixed** per (difficulty, period) — see `ChallengeXp.xpFor(...)`. (The model used to make up
  inconsistent numbers, so we took XP out of its hands.)

## Example implementation
```java
@Service
@RequiredArgsConstructor
public class FaithAiService implements KingdomAiService {
    private final OpenAiClient ai;
    public KingdomType kingdom() { return KingdomType.FAITH; }
    public String generateChallenge(Difficulty difficulty, Period period, List<String> existingChallenges) {
        String instructions = ChallengePrompts.build(
                "FAITH",
                "Completion is verified by ...",   // kingdom-specific
                "AI_PDF_MATCH",                    // verificationSource to echo back
                "\"...\"",                         // allowed metricKey(s)
                difficulty, period, existingChallenges);
        return ai.generate(instructions, "Generate one new challenge now.");
    }
}
```
