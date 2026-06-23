# Flow Contracts — Kingdom backend (Phase 3)

Contracts the 3 team members build against. **No implementation yet** — this defines *what* each flow
does so members can work in parallel without breaking each other.

> **Core product rule:** the **main source of challenges is AI**, not admin. A player picks a kingdom + a
> duration (DAILY / WEEKLY / MONTHLY / YEARLY) and the system **AI-generates** personalized challenges.
> Admin does **not** create normal challenges — admin handles seasonal events + manual review (Flow C).
> An optional admin "emergency manual create" endpoint may exist, but it is **not** the product flow.

> **Rewards = 3 currencies** (per kingdom, scaled by verified completion, written only by `RewardService`):
> **XP** → level/division (permanent) · **Seasonal Points** → season tiles + season leaderboard (reset each season) ·
> **Total Points** → all-time tiles + all-time leaderboard (permanent). Lobbies are Premium-only: **public** = same-division +
> **XP only** (no points/tiles/leaderboard); **private** = Twilio invite + **no rewards**. The **OPEN_CHALLENGE** kingdom is lobby-only and exempt from the free-2 limit. Lobby creation has **no user-facing difficulty step**; challenge selection shows **options only (no regenerate)**.

> Sources: `docs/Kingdom-Master-Spec.md`, `docs/Foundation-Summary.md`, `docs/Entity-Mapping.md`, `CLAUDE.md`.
> Companion docs: `API-Contracts.md` (endpoints), `Final-Team-Split.md` (authoritative ownership),
> `Flow-Ownership-Matrix.md` (who-owns-what grid), `Merge-Plan.md` (merge order), `Implementation-Order.md`.

---

## 0. Conventions every flow follows

**Dev-phase identity (security is `permitAll` for now).** The caller's identity is passed **explicitly** as
`playerId` (or `userId` for account ops). **Final security phase:** these are removed; identity comes from the
JWT principal. Every contract marks the **final role** (`PUBLIC` / `PLAYER` / `ADMIN`).

**Responses.** Controllers return `ResponseEntity<?>` wrapping `ApiResponse<T>` `{ success, message, data }`
(HTTP 200, or 201 on create). `ApiResponse<T>` and base `ApiException` live in `com.kingdom.API`. Failure →
`ApiError` `{ timestamp, status, message, path }` from `GlobalExceptionHandler`. Services throw the shared
exceptions (404/400/409/403/422). Controllers never build error bodies, never expose entities — map to a DTO.

**Relationships.** Plain `Integer` FK fields only. Services resolve links via repositories.

**Entity-write ownership (prevents merge collisions).** One writer service per entity, except the documented
shared-write entities in §4.

| Entity | Created / written by | Read by |
|---|---|---|
| `User`, `Player` | **M1** | all |
| `Kingdom` | **M1** (seed + optional admin create) | all |
| `Subscription` | M1 (read for premium gate; management deferred) | M1 |
| `KingdomMembership` | **created** by M1; **`xp`/`level`/`totalPoints` updated** by M3 (`RewardService`) | M2 (read), M3 |
| `Lobby` | **M1** (create / join / state; `division`-locked for public; `challengeId` set via `LobbyService.attachChallenge`) | M2, M3 (read) |
| `LobbyMember` | **M1** | M2, M3 (read) |
| `LobbyInvite` | **M1** (private invites; optional MVP) | M1 |
| `Challenge` | **M2** — `AiChallengeService` is the main creator (AI); optional admin emergency create also goes through M2's `ChallengeService` | M1, M3 (read) |
| `ChallengeProgress` | **M2 only** (admin approve/reject via delegation, §4) | M3 (read) |
| `ActivityRecord` | **M3 only** — `RewardService` is the *only* writer (reward ledger: xp/seasonal/total; sets `challengeProgressId`) | M2 (read) |
| `PeriodScore` | **M3** (`RewardService` — `seasonalPoints`) | M3 |
| `Notification` | **M3** | M3 |
| `HexTile`, `Badge`, `PlayerBadge` | not in Phase-3 required flows (deferred) | — |

---

# Flow A — User + Kingdom + Lobby Foundation (Member 1)

A1–A7 unchanged from the prior contract (create player, create admin, get profile, list kingdoms, kingdom
details, join kingdom, membership basics). Summarized here; full field-level rules below for the **new lobby**
flows A8–A11.

| # | Flow | Endpoint | Role | Service |
|---|---|---|---|---|
| A1 | Create player (User+Player) | `POST /api/users` | PUBLIC | `UserService.createPlayer` |
| A2 | Create admin | `POST /api/users/admin` | ADMIN¹ | `UserService.createAdmin` |
| A3 | Get profile | `GET /api/users/{playerId}` | PLAYER | `PlayerService.getProfile` |
| A4 | List kingdoms | `GET /api/kingdoms` | PUBLIC | `KingdomService.listKingdoms` |
| A5 | Kingdom details | `GET /api/kingdoms/{kingdomId}` | PUBLIC | `KingdomService.getKingdom` |
| A6 | Join kingdom | `POST /api/kingdoms/{kingdomId}/join` | PLAYER | `MembershipService.joinKingdom` |
| A7 | Membership basics | `GET /api/users/{playerId}/memberships[/{kingdomId}]` | PLAYER | `MembershipService.listMemberships`/`getMembership` |

¹ dev: open / bootstrap. Join rules: `UNIQUE(playerId,kingdomId)`; free cap = **2 normal kingdoms** via `countByPlayerIdAndActiveTrueAndKingdomIdNot(playerId, openChallengeKingdomId)`; the **OPEN_CHALLENGE** kingdom is **exempt** (doesn't count, lobby-only); premium for the 3rd+ normal kingdom.

### A8. Create lobby
- **Purpose:** Create a competition lobby tied to a kingdom; later an AI challenge is attached (B9/B10).
- **Entities:** `Lobby` (create). `Player` (host, read), `Kingdom` (read).
- **Repositories:** `LobbyRepository.save`, `PlayerRepository.findById`, `KingdomRepository.findById`, `SubscriptionRepository.findAllByUserId` (premium gate).
- **Service:** `LobbyService.createLobby(CreateLobbyRequest)`.
- **Endpoint:** `POST /api/lobbies` · **Role:** PLAYER (Premium).
- **Request DTO:** `CreateLobbyRequest` {hostPlayerId, kingdomId, visibility (PUBLIC/PRIVATE), startsAt, category?}. **No `difficulty`** — difficulty is backend-internal (AI balancing), not a user step; `Lobby.difficulty` stays optional/internal.
- **Response DTO:** `LobbyResponse` (lobbyId, kind=NORMAL, status=OPEN, challengeId=null, inviteCode if PRIVATE).
- **Validation:** kingdomId/visibility/startsAt `@NotNull`; `startsAt` in the future. (No `difficulty` — backend-internal.)
- **Success:** 201; `status=OPEN`, `challengeId=null` (challenge attached later), `kind=NORMAL`.
- **Failures:** host not premium → 403; unknown kingdom/host → 404; startsAt in the past → 400.
- **Business rules:** **creating a lobby requires Premium**; for **PUBLIC** lobbies `Lobby.division` is **locked to the host's division** in that kingdom (only same-division players may join — A9); **public duration ≤ 12h**, private custom; lobby auto-starts at `startsAt` (no manual start); host can't cancel if `< 8h` to start.

### A9. Join lobby
- **Purpose:** A player joins a lobby (public list, or private via invite code).
- **Entities:** `LobbyMember` (create), `Lobby` (read), `LobbyInvite` (read, private).
- **Repositories:** `LobbyMemberRepository` (`findByLobbyIdAndPlayerId`, `save`), `LobbyRepository.findById`, (`LobbyRepository.findByInviteCode` for private).
- **Service:** `LobbyService.joinLobby(lobbyId, playerId)`.
- **Endpoint:** `POST /api/lobbies/{lobbyId}/join` · **Role:** PLAYER.
- **Request DTO:** `JoinLobbyRequest` {playerId, inviteCode?}.
- **Response DTO:** `LobbyMemberResponse` / `LobbyResponse`.
- **Validation:** playerId `@NotNull`; inviteCode required when lobby is PRIVATE.
- **Success:** 201; `MemberRole=MEMBER` (host is `HOST`).
- **Failures:** already a member → 409; **public lobby division mismatch (joiner's division ≠ `Lobby.division`) → 403**; private lobby without/with wrong invite code → 403; lobby not OPEN → 400; unknown lobby → 404.
- **Business rules:** public lobbies are **same-division only**; private lobbies are invite-only — the invite is sent by **Twilio SMS** and accepted via the Twilio webhook (`LobbyInvite.inviteCode`, statuses incl. `EXPIRED`).

### A10. View lobby details / list
- **Purpose:** Preview a lobby before joining; browse public lobbies.
- **Entities:** `Lobby`, `LobbyMember` (count), `Challenge` (read, if attached).
- **Repositories:** `LobbyRepository` (`findById`, `findAllByVisibilityAndStatus`), `LobbyMemberRepository.findAllByLobbyId`, `ChallengeRepository.findById`.
- **Service:** `LobbyService.getLobby(lobbyId)` / `listPublicLobbies()`.
- **Endpoints:** `GET /api/lobbies/{lobbyId}` · `GET /api/lobbies?status=OPEN&visibility=PUBLIC` · **Role:** PLAYER.
- **Response DTO:** `LobbyResponse` (with memberCount + attached challenge summary if any).
- **Business rules:** the browse list shows **public** lobbies only; private are invite-only.

### A11. Attach challenge to lobby *(internal hook — called by M2)*
- **Purpose:** Set `Lobby.challengeId` once M2 has generated/persisted the lobby's AI challenge (keeps `Lobby` single-writer = M1).
- **Service contract:** `LobbyService.attachChallenge(lobbyId, challengeId)` → updates `Lobby.challengeId`.
- **Called by:** `AiChallengeService` (M2) in flow **B10**. Not a public endpoint of its own (exposed via B10).
- **Business rules:** a lobby holds exactly one challenge (`scope=LOBBY`); attaching twice replaces it only while `status=OPEN`.

---

# Flow B — AI Challenge Generation + Submission + Verification (Member 2)

> **This is the core engine.** Challenges are AI-generated, not admin-authored. Lifecycle of a run:
> **JOINED → IN_PROGRESS → SUBMITTED → (VERIFIED | REJECTED | EXPIRED)**.

### B0. AI generation engine design (structure, not an endpoint)
- **`AiChallengeService`** — orchestration: reads the player's `KingdomMembership` (xp / level / `getDivision()`),
  the `Kingdom` (`verificationSource`), the requested `period`, optional `goal`, and (for lobbies) the `Lobby`;
  asks the generator for options; on **select** persists the chosen option as a `Challenge`.
- **`AiChallengeGenerator`** *(interface / strategy)* — `List<AiChallengeOption> generate(GenerateContext ctx)`.
- **`MockAiChallengeGenerator`** *(rule-based impl for the demo)* — per-kingdom + per-period templates that scale
  `targetValue`/`xpReward` by division/difficulty. **Structured so a real AI API impl can replace it later**
  (a future `client`-package adapter implementing `AiChallengeGenerator`). **Do not call a real paid AI API** unless approved.
- **Generated option fields:** `title`, `description`, `kingdomId`, `period` (duration), `difficulty`,
  `xpReward`, `verificationType`, `metricKey?`, `targetValue?`, `safetyNotes?`.
  - Examples — Fitness daily: "Walk 7,000 steps today" (metricKey=`steps`, targetValue=7000). Study weekly:
    "Complete 5 study sessions" (metricKey=`sessions`, targetValue=5). Reading daily: "Read 20 pages" (`pages`,20).
- **Persistence model:** generation returns **transient option DTOs** (nothing saved). A `Challenge` row is
  persisted **only on select/attach** (`aiGenerated=true`). Unselected options are never stored.
- **Model support (applied):** `Challenge` now has `metricKey` / `targetValue` / `safetyNotes` / `active`. On
  select/attach the option's metric+target persist; verification reads the **stored** `targetValue` (never a
  client value); `active=false` disables the challenge (can't be started, hidden from active-only listings).

### B1. Personal AI challenge generation
- **Purpose:** Generate AI challenge options for a player in a kingdom + duration.
- **Entities:** `KingdomMembership` (read — division), `Kingdom` (read). No write.
- **Repositories:** `KingdomMembershipRepository.findByPlayerIdAndKingdomId`, `KingdomRepository.findById`.
- **Service:** `AiChallengeService.generatePersonal(GenerateChallengeRequest)`.
- **Endpoint:** `POST /api/challenges/generate` · **Role:** PLAYER. Returns **one** options list to choose from — **no user-facing regenerate / AI-prompt controls** in the normal flow.
- **Request DTO:** `GenerateChallengeRequest` {playerId, kingdomId, period (DAILY/WEEKLY/MONTHLY/YEARLY)}. (No custom `goal` prompt in the normal flow — custom prompts are private-lobby only, B9.)
- **Response DTO:** `AiChallengeOptionsResponse` {kingdomId, period, division, options:[`AiChallengeOption`]}.
- **Validation:** playerId/kingdomId/period `@NotNull`.
- **Success:** 200 + N options (transient).
- **Failures:** player not a member of the kingdom → 400 (join first); unknown kingdom → 404.
- **Business rules:** options are tailored to the player's division; difficulty is relative to division.

### B2. Select / start an AI-generated challenge
- **Purpose:** Persist a chosen option as a `Challenge`, then begin a run.
- **Entities:** `Challenge` (create, `aiGenerated=true`, `scope=SOLO`), `ChallengeProgress` (create), `KingdomMembership` (read).
- **Repositories:** `ChallengeRepository.save`, `ChallengeProgressRepository` (`findByChallengeIdAndMembershipId`, `save`), `KingdomMembershipRepository.findByPlayerIdAndKingdomId`.
- **Service:** `AiChallengeService.selectAndStart(SelectChallengeRequest)` (persists Challenge) → `SubmissionService.startChallenge(...)`.
- **Endpoint:** `POST /api/challenges/select` · **Role:** PLAYER.
- **Request DTO:** `SelectChallengeRequest` {playerId, option (`AiChallengeOption`)} *(stateless: the chosen option is returned by the client)*.
- **Response DTO:** `ChallengeProgressResponse` (status=JOINED) (+ the persisted challengeId).
- **Validation:** option fields validated/sanitized server-side before persist (anti-tamper: clamp xpReward/targetValue to generator bounds).
- **Success:** 201; Challenge persisted (`aiGenerated=true`), run created (`status=JOINED`).
- **Failures:** player not a kingdom member → 400; option fails sanitization → 400.
- **Business rules:** server re-validates the option (never trust client-sent xpReward/target blindly); one run per (challenge, membership).

### B3. List challenges by kingdom
- **Purpose:** Show persisted challenges in a kingdom (seasonal events, lobby challenges, the player's selected ones).
- **Repositories:** `ChallengeRepository.findAllByKingdomId` / `findAllByKingdomIdAndScope`.
- **Service:** `ChallengeService.listByKingdom(kingdomId)`.
- **Endpoint:** `GET /api/challenges?kingdomId={id}` · **Role:** PLAYER · **Response:** `ChallengeResponse[]`.

### B4. Challenge details
- **Repositories:** `ChallengeRepository.findById` · **Service:** `ChallengeService.getChallenge(id)`.
- **Endpoint:** `GET /api/challenges/{challengeId}` · **Role:** PLAYER · **Response:** `ChallengeResponse` · **404** if unknown.

### B5. Start an existing challenge *(seasonal / lobby / shared)*
- **Purpose:** Begin a run for an already-persisted challenge (e.g. a seasonal event or lobby challenge).
- **Entities:** `ChallengeProgress` (create), `Challenge` (read), `KingdomMembership` (read).
- **Service:** `SubmissionService.startChallenge(playerId, challengeId)`.
- **Endpoint:** `POST /api/submissions/start` · **Role:** PLAYER · **Request:** `StartChallengeRequest` {playerId, challengeId}.
- **Response:** `ChallengeProgressResponse` (status=JOINED). **409** if already started; **400** if not a kingdom member **or challenge inactive (`active=false`)**; **404** unknown challenge.
- **Business rule:** a challenge with `active=false` **cannot be started** (player listings use the active-only finders); existing runs are unaffected.
- **Note:** B2 (select a fresh AI challenge) calls this internally after persisting the Challenge.

### B6. Submit activity / proof
- **Entities:** `ChallengeProgress` (update). **Service:** `SubmissionService.submit(progressId, SubmitProofRequest)`.
- **Endpoint:** `POST /api/submissions/{progressId}/submit` · **Role:** PLAYER · **Request:** `SubmitProofRequest` {rawValue?, proofUrl?, metricValue?}.
- **Success:** 200, `status=SUBMITTED`, `submittedAt=now`. **400** bad state; **404** unknown.
- **Business rules:** **API-verifiable** challenges proceed to B7 auto-verify; **manual** proof stays `SUBMITTED` for admin review (C6/C7). Submit awards **no** XP.

### B7. Mock fitness verification (API path)
- **Purpose:** Auto-verify a submission via the mock client, then award.
- **Entities:** `ChallengeProgress` (update), `Challenge` (read target/xpReward), `ActivityRecord` (written by M3 in award).
- **Client:** `MockFitnessApiClient` (`client` package) — deterministic metric for a given externalId.
- **Service:** `VerificationService.verifyByMockFitness(MockFitnessRequest)`.
- **Endpoint:** `POST /api/verifications/fitness/mock` · **Role:** PLAYER (represents the provider webhook).
- **Request DTO:** `MockFitnessRequest` {progressId, source?=`MOCK_FITNESS`, externalId, metricValue}.
- **Response DTO:** `VerificationResultResponse` {progressId, status, verifiedBy, awardedXp, newXp, division, message}.
- **Success:** 200. Backend reads the metric **inside `[challengeStartAt, challengeEndAt]`**, sets `verifiedValue`, computes `completionRate = min(100%, verifiedValue / targetValue)`, scales the three rewards by it, sets `ChallengeProgress` → VERIFIED, verifiedBy=API, `xpEarned`/`seasonalPointsEarned`/`totalPointsEarned`, then **calls** `RewardService.awardReward(...)` (M3). Full completion → full reward; partial → pro-rated (metric challenges only).
- **Failures:** target not met → `attempts++`, status REJECTED, `rejectionReason=NOT_COMPLETED`, **no XP**, 422; duplicate `(source,externalId)` or `challengeProgressId` → idempotent no-op; unknown progress → 404.
- **Target source:** compare the **actual** verified value against the **stored** `Challenge.targetValue` (matched by `metricKey`). The client **never** supplies the target — `MockFitnessRequest.metricValue` is only the actual value being checked.
- **Business rules:** **only `RewardService` writes XP**; failing never subtracts XP.

### B8. Verification status / log
- **Entities:** `ChallengeProgress`. **Service:** `VerificationService.getStatus(progressId)`.
- **Endpoint:** `GET /api/verifications/{progressId}` · **Role:** PLAYER · **Response:** `ChallengeProgressResponse` (attempts, status, verifiedBy, rejectionReason).
- **Run traceability:** the awarded `ActivityRecord` carries `challengeProgressId` → findable via `findByChallengeProgressId`; `UNIQUE(challengeProgressId)` blocks a duplicate award. Failed attempts create no ledger row (just `attempts`/`rejectionReason`).

### B9. Lobby AI challenge generation
- **Purpose:** The lobby host generates AI task options for the lobby's kingdom.
- **Entities:** `Lobby` (read), `KingdomMembership`/`Kingdom` (read). No write.
- **Repositories:** `LobbyRepository.findById`, `KingdomRepository.findById`.
- **Service:** `AiChallengeService.generateForLobby(lobbyId, GenerateLobbyChallengeRequest)`.
- **Endpoint:** `POST /api/challenges/lobby/{lobbyId}/generate` · **Role:** PLAYER (host, Premium). **Public lobby:** returns one options list (no regenerate, no prompt). **Private lobby:** the host may pass a custom `goal` prompt.
- **Request DTO:** `GenerateLobbyChallengeRequest` {playerId, period?, goal? *(custom prompt — PRIVATE lobby only)*}.
- **Response DTO:** `AiChallengeOptionsResponse`.
- **Failures:** caller not the lobby host → 403; lobby not OPEN → 400; unknown lobby → 404.

### B10. Attach chosen lobby challenge
- **Purpose:** Persist the chosen option as a `Challenge` (`scope=LOBBY`, `aiGenerated=true`) and attach it to the lobby.
- **Entities:** `Challenge` (create), `Lobby` (`challengeId` set **via M1**).
- **Repositories:** `ChallengeRepository.save`.
- **Service:** `AiChallengeService.attachToLobby(lobbyId, AttachLobbyChallengeRequest)` → persists Challenge → calls `LobbyService.attachChallenge(lobbyId, challengeId)` (M1).
- **Endpoint:** `POST /api/challenges/lobby/{lobbyId}/attach` · **Role:** PLAYER (host).
- **Request DTO:** `AttachLobbyChallengeRequest` {playerId, option}.
- **Response DTO:** `LobbyResponse` (now with challengeId) / `ChallengeResponse`.
- **Business rules (reward scoping, LOCKED):** **public lobby → XP only** (NO seasonal/total points → **no tiles, no leaderboard**); **private lobby → no rewards at all**. Encoded at creation: the persisted public-lobby `Challenge` has `seasonalPointsReward=0` and `totalPointsReward=0` (so `RewardService` adds XP only); a private-lobby challenge awards nothing (the flow never calls `RewardService`).

### B-admin (optional). Emergency manual challenge create
- **Purpose:** Rare admin/manual create when AI is unavailable — **not** the product flow.
- **Service:** `ChallengeService.createChallenge(CreateChallengeRequest)` (same `Challenge` writer = M2).
- **Endpoint:** `POST /api/challenges` · **Role:** ADMIN · **Optional.** Document as a fallback only; the main path is B1/B2.

---

# Flow C — XP + Leaderboard + Admin Events / Review (Member 3)

C1–C10 mostly as before; **admin is events + review, never the main challenge creator.**

### C1. Award rewards *(internal — only RewardService writes XP/points)*
- **Service contract:** `RewardService.awardReward(membershipId, challengeProgressId, xpAwarded, seasonalPointsAwarded, totalPointsAwarded, source, externalId, challengeId) → RewardResult{newXp, newTotalPoints, newSeasonalPoints, level, division}`. The **caller has already scaled** the three amounts by `completionRate`.
- **Called by:** `VerificationService` (B7) and `AdminReviewService` (C6). **No other service writes XP/points.**
- **Rules:** idempotent via `existsByChallengeProgressId` **or** `existsBySourceAndExternalId`; create one `ActivityRecord{challengeProgressId, source, externalId, xpAwarded, seasonalPointsAwarded, totalPointsAwarded, status=VERIFIED}`; `membership.xp += xpAwarded`; `membership.totalPoints += totalPointsAwarded`; active `PeriodScore.seasonalPoints += seasonalPointsAwarded`; recompute `level`; division/tiles stay derived.

### C2–C5. Division / Progress / XP history / Leaderboard
- C2 **Division** — derived `getDivision()`, never stored.
- C3 **Progress summary** — `GET /api/progress/players/{playerId}[/kingdoms/{kingdomId}]` · `ProgressService` · `ProgressSummaryResponse`/`KingdomProgressResponse`.
- C4 **XP history** — `GET /api/progress/players/{playerId}/xp-history?kingdomId=` · `ActivityRecordResponse[]`.
- C5 **Leaderboard** — `GET /api/leaderboards/kingdoms/{kingdomId}?period=&division=` · `LeaderboardService` · division-scoped, rank computed.

### C6. Admin approve manual submission
- **Service:** `AdminReviewService.approve(progressId, ApproveSubmissionRequest)` → `SubmissionService.markApproved(progressId, ADMIN)` (M2 mutates `ChallengeProgress`) → `RewardService.awardReward(membershipId, progressId, xpAwarded, seasonalPointsAwarded, totalPointsAwarded, "ADMIN_REVIEW", "progress:"+progressId, challengeId)` → `NotificationService.create(...)`.
- **Endpoint:** `POST /api/admin/submissions/{progressId}/approve` · **Role:** ADMIN · **Response:** `ChallengeProgressResponse` (VERIFIED, verifiedBy=ADMIN). Idempotent.

### C7. Admin reject manual submission
- **Service:** `AdminReviewService.reject(progressId, RejectSubmissionRequest)` → `SubmissionService.markRejected(progressId, reason)` → `NotificationService.create`.
- **Endpoint:** `POST /api/admin/submissions/{progressId}/reject` · **Role:** ADMIN · **Request:** `RejectSubmissionRequest` {rejectionReason, note?}. No XP. `FLAGGED` = cheating, no retry.

### C8. Admin review queue
- **Repositories:** `ChallengeProgressRepository.findAllByStatus(SUBMITTED)` / `findAllByStatusIn(SUBMITTED, FLAGGED)`.
- **Endpoint:** `GET /api/admin/submissions/pending` · **Role:** ADMIN · **Response:** `ChallengeProgressResponse[]`.

### C-events. Admin seasonal event creation / start
- **Purpose:** Admin starts a public seasonal event inside a normal kingdom (e.g. a Ramadan challenge in Faith).
- **Entities:** `Lobby` (`kind=SEASONAL`, `createdByAdmin=true`, written **via M1**), `Challenge` (the event's challenge, written **via M2**).
- **Service:** `AdminEventService.createEvent(CreateEventRequest)` → `ChallengeService.createChallenge(...)` (M2, persists the event challenge) → `LobbyService.createSeasonalLobby(...)` (M1, `kind=SEASONAL`, `createdByAdmin=true`, links the challenge).
- **Endpoint:** `POST /api/admin/events` · **Role:** ADMIN · **Request:** `CreateEventRequest` {kingdomId, title, description, period, difficulty, xpReward, startsAt, endsAt}.
- **Response DTO:** `EventResponse` (lobbyId, challengeId, kingdomId, startsAt, endsAt).
- **Business rules:** events **reuse `Lobby`** (`kind=SEASONAL`) — no separate Event entity (`docs/Entity-Mapping.md`). Anyone can join the event; XP follows normal verified rules. The event challenge may be AI-assisted (via M2's `AiChallengeService`) or admin-specified.

### C-disable. Disable / report a bad AI challenge *(buildable now)*
- **Purpose:** Admin moderation of unsafe AI output.
- **Entities:** `Challenge` (`active=false`, written **via M2**).
- **Service:** `AdminEventService.disableChallenge(challengeId)` → `ChallengeService.setActive(challengeId, false)` (M2 stays the sole `Challenge` writer).
- **Endpoint:** `POST /api/admin/challenges/{id}/disable` · **Role:** ADMIN.
- **Business rules:** `active=false` blocks new starts (B5) and hides the challenge from active-only listings; existing `ChallengeProgress` / XP history is **never** deleted. (Optional beyond the MVP-3 admin duties, but supported by the model.)

### C9. Notifications *(optional)* · C10. Demo data *(M3 owns `DemoDataSeeder`)* — unchanged.

---

## 4. Cross-flow integration contracts (read before coding)

**Call graph (who calls whom):**
```
B2  AiChallengeService (M2)  ──persist Challenge──▶  ChallengeRepository
B2  AiChallengeService (M2)  ──startChallenge────▶  SubmissionService (M2)
B7  VerificationService (M2) ──awardReward───────────▶  RewardService (M3)
B10 AiChallengeService (M2)  ──attachChallenge───▶  LobbyService (M1)   [sets Lobby.challengeId]
C6  AdminReviewService (M3)  ──markApproved/Rejected─▶ SubmissionService (M2)
C6  AdminReviewService (M3)  ──awardReward───────────▶  RewardService (M3)
C-events AdminEventService(M3) ──createChallenge─▶  ChallengeService (M2)   and ──createSeasonalLobby─▶ LobbyService (M1)
A6  MembershipService (M1)   ──reads────────────▶  SubscriptionRepository
B1/B5 (M2)                   ──reads────────────▶  KingdomMembershipRepository (M1's entity)
```

**Agreed service interfaces (freeze these signatures first → members stub & build in parallel):**
- `AiChallengeGenerator.generate(GenerateContext ctx) → List<AiChallengeOption>` *(M2; mock now, real-AI adapter later)*
- `RewardService.awardReward(Integer membershipId, Integer challengeProgressId, int xpAwarded, int seasonalPointsAwarded, int totalPointsAwarded, String source, String externalId, Integer challengeId) → RewardResult` *(M3; caller pre-scales the three amounts by completionRate)*
- `SubmissionService.markApproved(Integer progressId, VerifierType by) → ChallengeProgress` · `markRejected(Integer progressId, RejectionReason reason) → ChallengeProgress` *(M2)*
- `LobbyService.attachChallenge(Integer lobbyId, Integer challengeId)` · `createSeasonalLobby(...) → Lobby` *(M1)*
- `ChallengeService.createChallenge(CreateChallengeRequest) → Challenge` *(M2; used by admin emergency + seasonal events)*
- `NotificationService.create(Integer playerId, NotificationType type, String title, String body, String linkRef)` *(M3)*

**Shared-write entities & protocol:**
1. **`KingdomMembership`** — M1 creates (join); only M3's `RewardService` mutates `xp`/`level`; M2 reads.
2. **`ChallengeProgress`** — only M2 writes; admin approve/reject (M3) goes through M2's `markApproved/markRejected`.
3. **`Lobby`** — only M1 writes; M2 attaches a challenge through `LobbyService.attachChallenge`; M3 starts seasonal lobbies through `LobbyService.createSeasonalLobby`.

**Idempotency keys (anti-cheat):**
- **Per run:** `ActivityRecord.challengeProgressId` UNIQUE → one award per `ChallengeProgress`.
- **Per external event:** `ActivityRecord(source, externalId)` UNIQUE → API award `source="MOCK_FITNESS"`, externalId = workout id; admin award `source="ADMIN_REVIEW"`, externalId = `"progress:"+progressId`.

**Assumptions flagged:**
- **AI generation and lobbies are now IN Phase-3 MVP scope** (previously deferred).
- AI generation is **mock/rule-based** (`MockAiChallengeGenerator`); no real paid AI API without approval.
- Generated options are transient; a `Challenge` row persists only on select/attach (`aiGenerated=true`).
- Premium gate reads `Subscription`; no row ⇒ FREE. Subscription management deferred.
- Hex map, badges, real provider OAuth, realtime remain deferred.

---

## 5. Model change — APPROVED & APPLIED

`Challenge` now carries the AI-verification fields (all nullable except `active`, which defaults `true`):
```java
private String  metricKey;     // what to verify: STEPS, WORKOUT_MINUTES, CALORIES, DISTANCE_KM, STUDY_MINUTES, PAGES_READ, VOLUNTEER_HOURS
private Integer targetValue;   // the TRUSTED server-side target the verifier compares against
@Column(length = 500)
private String  safetyNotes;   // AI safety / warnings (e.g. "Stop if you feel pain", "Manual proof required")
private Boolean active = true;  // false = disabled (can't be started), without deleting it
```
`ChallengeRepository` gained `findAllByKingdomIdAndActiveTrue(kingdomId)` and
`findAllByKingdomIdAndPeriodAndActiveTrue(kingdomId, period)` (active-only player listings; `period` = `Period` enum).

**Business rules (enforced in services, Phase 3):**
- AI-generated challenges set `aiGenerated=true`, plus `metricKey` + `targetValue` when metric-verifiable.
- **Verification compares the actual verified value against the stored `Challenge.targetValue`** (matched by
  `metricKey`). **The client never controls the target** — the verify request's `metricValue` is only the actual value.
- `active=false` → players **cannot start** the challenge (B5); player-facing listings use the active-only finders.
- Disabling a challenge **never deletes** existing `ChallengeProgress` / XP history.
- `safetyNotes` is surfaced to the player on the challenge detail / option.

Backward-compatible: `ddl-auto=update` adds the columns; a fresh DB builds them from the entity.

**Also applied in the same pass (3-currency + competition model):**
- `KingdomMembership.totalPoints`; `PeriodScore.xp → seasonalPoints` (+ derived `getSeasonTiles()`); `KingdomMembership.getTilesOwned()` now derives from `totalPoints` (all-time tiles).
- `Challenge.seasonalPointsReward`/`totalPointsReward`; `ChallengeProgress.challengeStartAt`/`challengeEndAt`/`verifiedValue`/`completionRate`/`seasonalPointsEarned`/`totalPointsEarned`; `ActivityRecord.seasonalPointsAwarded`/`totalPointsAwarded`.
- `Lobby.division` (public same-division lock); `LobbyInvite.inviteCode` (Twilio reply matching).
- Enums: `KingdomType.SPECIAL_EVENTS → OPEN_CHALLENGE`; `InviteChannel +SMS`; `InviteStatus +EXPIRED`.
- Repos: `KingdomMembershipRepository.countByPlayerIdAndActiveTrueAndKingdomIdNot` / `findAllByKingdomIdAndActiveTrue`; `ChallengeProgressRepository.findAllByMembershipIdAndStatusIn`; `PeriodScoreRepository.findAllByMembershipIdInAndPeriodAndPeriodStart`.
- Service renamed **`XpService → RewardService`** (`awardReward(...)` writes all 3 currencies). Verified `mvn compile` → BUILD SUCCESS.
