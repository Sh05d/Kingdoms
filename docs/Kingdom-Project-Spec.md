# Kingdom — Project Spec & Handoff

> Single-file context for picking the project up in a fresh session. Covers the idea, the architecture, Anas's
> scope, the full flow, config, how to run/test, current state, and the gotchas we hit. Anas = git author `Anasm-ksa`.

## 1. What it is
A gamified "do real things, get them verified, earn XP" backend. A user **registers → verifies an OTP → gets a
welcome email**, joins kingdoms (themed activity areas), takes on AI-generated **challenges**, and to complete one
must pass that kingdom's **real external verification**. On a verified finish they get **XP + a daily streak** and a
**hype WhatsApp**. **Lobbies** are head-to-head races: first member/host to finish wins bragging rights (no XP).

## 2. Stack
- Spring Boot **4.0.6**, Java **17**, Maven, MySQL.
- **Jackson 3** (`tools.jackson.databind.*` for `JsonNode`/`ObjectMapper`; annotations still `com.fasterxml.jackson.annotation`).
- Lombok, ModelMapper, `RestTemplate` for outbound HTTP. `spring-boot-starter-mail`, Twilio SDK.
- Package root `com.kingdom`: `Controller`, `Service` (+ `Service/AiService`, `Service/APIService`), `verification`,
  `Model`, `Repository`, `Enums`, `DTO/IN` + `DTO/OUT`, `API` (`ApiResponse`, `ApiException`), `advice`
  (`GlobalExceptionHandler`), `Config`, `seed`.

## 3. Team split — 9 kingdoms, 3 people (by kingdom, not feature)
Two people never touch the same kingdom; all merge conflicts are in the **shared scaffolding**.
- **Anas (me):** SPORTS (Fitness) / CHARITY / VOLUNTEERING. Models: **Challenge, ChallengeProgress, ConnectedAccount**.
- **Shahad:** READING / GAMING / FAITH. Services: ReadingAiService, GamesAiService, FaithAiService (stub),
  SteamService, GoogleBooksService, EmailService, `ChallengeQuestion` entity.
- **Maysun (User/Kingdom flow):** User, Player, Kingdom, KingdomMembership, Subscription, Lobby, Badge.
- **Teammate 3 (pending):** the remaining KingdomTypes KNOWLEDGE / NUTRITION / PROGRAMMING — not built yet.

`KingdomType` enum (9): SPORTS, KNOWLEDGE, CHARITY, GAMING, VOLUNTEERING, FAITH, NUTRITION, READING, PROGRAMMING.
`UserRole` enum: HOST, MEMBER, ADMIN (MEMBER is the normal player; there is **no PLAYER** value).

## 4. The full system flow (target design)
register → **OTP verify** → **welcome email** → join up to **2 kingdoms free** (more = subscription) → per-kingdom
**level/division + streak** → browse/**filter challenges** (difficulty/period) → **join many** → **finish** gated on the
kingdom's verification → **XP + daily streak + hype WhatsApp** → **lobbies** race for bragging rights (no XP).
The **AI authors challenges only within what each kingdom's API can verify** (so nothing un-checkable).

## 5. Verification engines (finish dispatch in `ChallengeProgressService.finishChallenge`)
Branches on `challenge.verificationSource`:
- **STRAVA** (Fitness) → `FitnessVerificationService.hasReached(metricKey,target,…)` via `StravaClient`. Metrics:
  `distance_meters` / `moving_time_seconds` / `activity_count`. **Per-player OAuth**: each player connects their own
  Strava (`/verify/fitness/connect/{playerId}` → authorize → `/verify/fitness/callback` stores their refresh token on
  a `ConnectedAccount(provider=STRAVA)`); finish reads the finishing player's token, falling back to a configured demo
  athlete. **Window = the challenge's PERIOD ending now** (WEEKLY→7d, DAILY→today) — activities within the period count.
- **NEOTEK_OPEN_BANKING** (Charity) → `CharityVerificationService.hasDonated(psuId,…)` via `NeotekClient` (Saudi open
  banking; FI = `SAIBCSARI`/Saib). Reads booked debit transactions. Demo helper `POST /verify/charity/donate` records
  a simulated donation so a finish can pass (sandbox has no real charities). PSU resolved from the player's NEOTEK
  `ConnectedAccount.externalUserId`, fallback `neotek.demo-psu-id=20112`.
- **AI_PDF_MATCH** (Volunteer) → push-based: finish *prompts* the player on WhatsApp to send a certificate PDF. The PDF
  (via the WhatsApp webhook or `POST /verify/volunteer/upload`) goes through `VolunteerVerificationService.verifyCertificate`
  (OpenAI reads the PDF) → on approval `completeVolunteerByPhone` matches the sender by phone → completes the run + XP.
- **STEAM** (Gaming, Shahad) → `steamCheck`. **GOOGLE_BOOKS** (Reading) and FAITH currently have **no finish verifier**
  (they hit the `else → pass`) — a known teammate gap (see TODO).

## 6. XP / division / streak (all on `KingdomMembership`, per kingdom)
- On a verified finish, `markVerified` does: `updateStreak` → set VERIFIED + finishedAt → add `challenge.xpReward` to
  `totalXP` → recompute `division` → save.
- **Division** (lower = higher tier): D1 ≥ 25,000 | D2 10,000–24,999 | D3 0–9,999. (Stored as raw int 1/2/3.)
  There is **no separate "level"** — division is the per-kingdom tier.
- **Streak** (`strick`, per kingdom, daily): +1 if a challenge was also finished in that kingdom yesterday, unchanged if
  already finished today, reset to 1 if a day was missed.
- **XP is fixed** per (difficulty, period) via `ChallengeXp` (DAILY 50/80/120, WEEKLY 100/140/180, MONTHLY 200/250/300,
  YEARLY 400/500/600 for EASY/MEDIUM/HARD). The AI never sets XP.

## 7. WhatsApp (Twilio) — hype + PDF
- **Outbound hype** (`WhatsAppService.sendMessage`, best-effort, never blocks a flow): on **join**, on **verified finish**,
  and on **lobby win** (no XP).
- **Inbound** (one Twilio "when a message comes in" webhook): `POST /api/v1/verify/volunteer/whatsapp` — receives the
  PDF, verifies, completes the player's active volunteer run. **Only one inbound URL exists** in the Twilio sandbox, so at
  the team merge a **router** endpoint is needed (PDF → volunteer; text/list-tap → Shahad's reading quiz). See TODO.
- A teammate also has `POST /api/v1/whatsapp/twilio` (onboarding) — competes for the same single webhook.

## 8. AI challenge generation
`POST /api/v1/challenge/generate?kingdomId&difficulty&period` → `ChallengeService.generateChallenge` → `KingdomAiRegistry`
picks the kingdom's `KingdomAiService` → OpenAI (model gpt-5.5) returns JSON → regex-parsed → **validated** (source must
be a real verifier source + Strava/Neotek need a positive target, else rejected) → fixed XP → saved. Each AI prompt
(`ChallengePrompts.build` + per-kingdom service) constrains the AI to the exact `verificationSource` + `metricKey` its
verifier understands. (Shahad's `POST /challenge/kingdom/{id}/generate` is the JSON path for Reading/Gaming with questions.)

## 9. Config (`application.properties` has `${ENV:default}`; real secrets in git-ignored `application-local.properties`)
- DB: `localhost:3307/Data`, root/root (local props override the tracked default). `ddl-auto=update` locally (so data +
  Strava connection persist across restarts; the tracked value is `create-drop` for the team).
- Keys in local props: `openai.api-key`, `strava.client-id/secret/refresh-token`, `neotek.client-id/secret`,
  `twilio.account-sid/auth-token`, `twilio.whatsapp-from=whatsapp:+14155238886`.
- `demo.seed.phone=+966554412209` — the seeder puts this on the demo user so the hype/PDF reach the real phone.
- Strava OAuth callback domain must be `localhost` in the Strava app settings; `strava.redirect-uri` →
  `http://localhost:8080/api/v1/verify/fitness/callback`.

## 10. Seeder + Postman + endpoints
- **`com.kingdom.seed.DemoSeeder`** (CommandLineRunner) creates on boot: user `anas` + player + the 3 kingdoms
  (SPORTS=1, CHARITY=2, VOLUNTEERING=3) + a membership in each (saved via repo to bypass the 2-kingdom cap). Deterministic
  ids on a fresh DB: **playerId=1**. Toggle with `demo.seed.enabled=false`.
- Postman collections (repo root): `Kingdom-Anas-3Kingdoms-Flow.postman_collection.json` (the 3-kingdom flow incl. Strava
  consent) and `Kingdom-WhatsApp-Test.postman_collection.json` (hype + inbound PDF + volunteer loop).
- All 41 of Anas's endpoints are listed in **`docs/Anas-Endpoints.md`**.

## 11. Repo / branches (GitHub `Anas-als3/Kingdoms`)
- **`anas-shahad-merge`** (local + `origin/anas-shahad-merge`): the integrated branch (Anas + Shahad merged). **Active work
  is here.** A large amount is **uncommitted** on it (seeder, both Postman collections, Strava consent, ConnectedAccount
  unique constraint, WhatsApp hype + volunteer loop, the window fix, the join/lobby hypes, the 4 correctness fixes, this
  spec + TODO). Commit when the user approves; never push without explicit approval.
- `anas-ai`: Anas's pre-merge branch. `origin/Shahad`: teammate (a NEW push is pending a re-merge). `main`: do not touch.

## 12. Current state — done vs gaps (from a full 10-step audit)
- ✅ **Done (Anas):** filter+join challenges, per-kingdom verification, XP/division/streak, hype (join/finish), lobby
  win (no XP), AI-constrained generation, per-player Strava consent, volunteer-via-WhatsApp.
- ❌ **Missing (teammate-owned):** (1) **register + OTP verify** — no `/register` or `/verify-otp`; `sendOtp` exists but is
  unwired (Twilio Verify is the natural fit). (2) **Welcome email** — `EmailService` is 100% commented out and uncalled.
  (3) **Subscription gate is weak** — the 2-kingdom cap is lifted by ANY non-null subscription (ignores PREMIUM/ACTIVE);
  `isPlayerPremium` exists but isn't used by the join gate.
- 🐛 **Known teammate bugs:** `/user/add` binds the raw entity → role self-assign (anyone can create ADMIN) + plaintext
  password + password hash serialized in `GET /user/get`. Division reads can NPE on null totalXP.
- Backlog + deferred items: **`TODO.md`** (volunteer cert anti-cheat: date/hours/reuse/name; security final pass incl.
  Strava OAuth `state` CSRF; the WhatsApp inbound router; lenient phone matching).

## 13. Gotchas / lessons (save future-you time)
- **One inbound WhatsApp webhook** for the whole team → needs a router at merge time. Hyping is *outbound* (no conflict).
- **Phone match is exact E.164** (`+966…`); set `demo.seed.phone` to the real number or the hype/PDF-completion won't match.
- **Strava verification window = the challenge period**, not since-join (was a UX trap when it was join→now).
- **`create-drop` wipes the DB every boot** (incl. the Strava OAuth token) — use `ddl-auto=update` locally to persist.
- **`mvn spring-boot:start` defaults to a 30s start timeout** — pass `-Dspring-boot.start.maxAttempts=120` for slow boots.
- **Security is intentionally off** for dev (`SecurityConfig` commented out) — real auth is the final-stage task.
- AI/OpenAI: model gpt-5.5, Responses API; needs a real key for generation + PDF verify.

## 14. How to run / test
1. `application-local.properties` present with the real keys + `demo.seed.phone` + `ddl-auto=update`.
2. MySQL on `localhost:3307` (db `Data`).
3. Run the app on **:8080** (`mvn spring-boot:run` or IDE) — the seeder creates player 1 + 3 kingdoms.
4. Import the Postman collections; run the 3-kingdom flow (Strava needs a one-time connect + a real activity; Charity uses
   the donate simulator; Volunteer uploads a PDF). WhatsApp: opt your number into the Twilio sandbox (`join <code>` to
   +14155238886) and point the sandbox webhook (via ngrok) at `/api/v1/verify/volunteer/whatsapp`.

_Last updated: 2026-06-19 (Anas)._
