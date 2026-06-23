# API Contracts — Kingdom backend (Phase 3)

Complete REST contract. Every success body is wrapped in `ApiResponse<T>`; every error body is the
`ApiError` shape from `GlobalExceptionHandler`. See `Flow-Contracts.md` for the full business rules.

- **Base paths:** `/api/users`, `/api/kingdoms`, `/api/lobbies`, `/api/challenges`, `/api/submissions`, `/api/verifications`, `/api/progress`, `/api/leaderboards`, `/api/admin` (+ optional `/api/notifications`).
- **Challenges are AI-generated** (`POST /api/challenges/generate`), not admin-authored. Admin = seasonal events + review.
- **Rewards = 3 currencies** (per kingdom): `xp` (level/division, permanent), `seasonalPoints` (season tiles + leaderboard, resets), `totalPoints` (all-time tiles + leaderboard). Scaled by verified `completionRate`; written only by `RewardService`. Lobbies: **public** = same-division + **XP only** (no points/tiles/leaderboard); **private** = Twilio invite + **no rewards**. Lobby creation has **no user-facing difficulty step**; challenge selection shows **options only (no regenerate)**.
- **Roles** are the **final** security target (`PUBLIC` / `PLAYER` / `ADMIN`). During dev everything is open (`permitAll`) and identity is passed as an explicit `playerId`/`userId`.

**Envelope examples**
```jsonc
// success
{ "success": true, "message": "Operation completed successfully", "data": { /* T */ } }
// error (from GlobalExceptionHandler)
{ "timestamp": "2026-06-16T00:00:00", "status": 404, "message": "Player 7 not found", "path": "/api/users/7" }
```

---

> `ApiResponse<T>` lives in **`com.kingdom.API`**; controllers return `ResponseEntity<?>` wrapping it. Errors come from `com.kingdom.advice.GlobalExceptionHandler` (`ApiError` `{timestamp,status,message,path}`); typed business exceptions are in `com.kingdom.exception`. See `Foundation-Summary.md` §7.

---

## 1. User APIs — `/api/users` (Member 1)

| Method | URL | Role | Purpose | Service method | Success | Key failures |
|---|---|---|---|---|---|---|
| POST | `/api/users` | PUBLIC | Create player (User+Player) | `UserService.createPlayer` | 201 | 409 dup email/username, 400 invalid |
| POST | `/api/users/admin` | ADMIN¹ | Create admin user | `UserService.createAdmin` | 201 | 409 dup, 400 invalid |
| GET | `/api/users/{playerId}` | PLAYER/ADMIN | Get profile | `PlayerService.getProfile` | 200 | 404 unknown |
| PUT | `/api/users/{playerId}` *(optional)* | PLAYER | Update profile/prefs | `PlayerService.updateProfile` | 200 | 404, 400, 409 colour taken |
| GET | `/api/users/{playerId}/memberships` | PLAYER/ADMIN | List memberships (basics) | `MembershipService.listMemberships` | 200 | 404 unknown player |
| GET | `/api/users/{playerId}/memberships/{kingdomId}` | PLAYER/ADMIN | One membership | `MembershipService.getMembership` | 200 | 404 not a member |

¹ dev: open / bootstrap-only.

**POST /api/users**
```jsonc
// request — CreatePlayerRequest
{ "email":"sara@x.com", "username":"sara", "phoneNumber":"+9665...", "displayName":"Sara",
  "colorHex":"#E63946", "interests":"running,reading", "language":"ar" }
// 201 — ApiResponse<PlayerProfileResponse>
{ "success":true, "message":"Player created", "data":{
  "playerId":1, "userId":1, "username":"sara", "email":"sara@x.com", "displayName":"Sara",
  "colorHex":"#E63946", "language":"ar", "interests":"running,reading", "role":"PLAYER",
  "joinedAt":"2026-06-16T00:00:00" } }
```
**Validation:** `email @Email @NotBlank`, `username @NotBlank @Size(3,30)`, `displayName @NotBlank`, `colorHex @Pattern(#?[0-9A-Fa-f]{6})`.

**POST /api/users/admin**
```jsonc
{ "email":"admin@x.com", "username":"admin", "phoneNumber":null }     // CreateAdminRequest
// 201 data: { "userId":2, "username":"admin", "role":"ADMIN", "playerId":null }
```

**GET /api/users/{playerId}/memberships**
```jsonc
// 200 — ApiResponse<List<MembershipResponse>>
{ "success":true, "message":"OK", "data":[
  { "membershipId":10, "playerId":1, "kingdomId":1, "kingdomName":"Sports",
    "xp":12000, "level":4, "division":"D2", "totalPoints":12500, "tilesOwned":125, "active":true, "joinedAt":"..." } ] }
```

---

## 2. Kingdom APIs — `/api/kingdoms` (Member 1)

| Method | URL | Role | Purpose | Service method | Success | Key failures |
|---|---|---|---|---|---|---|
| GET | `/api/kingdoms` | PUBLIC | List 10 kingdoms | `KingdomService.listKingdoms` | 200 | — |
| GET | `/api/kingdoms/{kingdomId}` | PUBLIC | Kingdom details + memberCount | `KingdomService.getKingdom` | 200 | 404 unknown |
| POST | `/api/kingdoms/{kingdomId}/join` | PLAYER | Join kingdom | `MembershipService.joinKingdom` | 201 | 409 already member, 403 cap/premium, 404 |
| POST | `/api/kingdoms` *(optional)* | ADMIN | Admin create kingdom | `KingdomService.createKingdom` | 201 | 409 type exists, 400 |

**GET /api/kingdoms**
```jsonc
// 200 — ApiResponse<List<KingdomResponse>>
{ "success":true, "message":"OK", "data":[
  { "kingdomId":1, "type":"SPORTS", "name":"Sports", "nameAr":"الرياضة",
    "premiumOnly":false, "verificationSource":"Apple Health / Google Fit", "memberCount":42 } ] }
```

**POST /api/kingdoms/{kingdomId}/join**
```jsonc
{ "playerId":1 }                                  // JoinKingdomRequest (dev; final = principal)
// 201 — ApiResponse<MembershipResponse>
{ "success":true, "message":"Joined Sports", "data":{
  "membershipId":10, "playerId":1, "kingdomId":1, "kingdomName":"Sports",
  "xp":0, "level":1, "division":"D3", "tilesOwned":0, "active":true, "joinedAt":"..." } }
// 409 → "Player already joined this kingdom" · 403 → "Free plan allows up to 2 kingdoms" / "Kingdom requires Premium"
```
**Validation:** `playerId @NotNull`; kingdomId path must exist.

---

## 2b. Lobby APIs — `/api/lobbies` (Member 1)

| Method | URL | Role | Purpose | Service method | Success | Key failures |
|---|---|---|---|---|---|---|
| POST | `/api/lobbies` | PLAYER (Premium) | Create a lobby | `LobbyService.createLobby` | 201 | 403 not premium, 404 kingdom, 400 startsAt past |
| GET | `/api/lobbies?status=OPEN&visibility=PUBLIC` | PLAYER | List public lobbies | `LobbyService.listPublicLobbies` | 200 | — |
| GET | `/api/lobbies/{lobbyId}` | PLAYER | Lobby details (preview) | `LobbyService.getLobby` | 200 | 404 |
| POST | `/api/lobbies/{lobbyId}/join` | PLAYER | Join a lobby | `LobbyService.joinLobby` | 201 | 409 already member, 403 invite, 400 not OPEN, 404 |

> The lobby's challenge is **AI-generated by Member 2** (`POST /api/challenges/lobby/{lobbyId}/generate` + `/attach`); `LobbyService.attachChallenge` (M1) sets `Lobby.challengeId`.

**POST /api/lobbies**
```jsonc
// request — CreateLobbyRequest
{ "hostPlayerId":1, "kingdomId":1, "visibility":"PUBLIC",
  "startsAt":"2026-06-20T18:00:00", "category":null }
// no `difficulty` field — difficulty is backend-internal (AI balancing), not a user step
// 201 — ApiResponse<LobbyResponse>
{ "success":true, "message":"Lobby created", "data":{
  "lobbyId":3, "hostPlayerId":1, "kingdomId":1, "kind":"NORMAL", "visibility":"PUBLIC",
  "difficulty":"MEDIUM", "division":"D2", "status":"OPEN", "challengeId":null, "startsAt":"2026-06-20T18:00:00",
  "endsAt":null, "inviteCode":null, "memberCount":1 } }
```
**Validation:** `kingdomId/visibility/startsAt @NotNull`; `startsAt` in the future. (No `difficulty` — backend-internal.)

**POST /api/lobbies/{lobbyId}/join**
```jsonc
{ "playerId":2, "inviteCode":null }                 // JoinLobbyRequest (inviteCode required if PRIVATE)
// 201 — ApiResponse<LobbyResponse> (memberCount incremented)
```

---

## 3. Challenge APIs — `/api/challenges` (Member 2) — **AI-generated**

> Challenges are generated by AI (`AiChallengeService` + `MockAiChallengeGenerator`), **not** authored by admin.
> Generation returns transient options; a `Challenge` row persists only on **select/attach** (`aiGenerated=true`).

| Method | URL | Role | Purpose | Service method | Success | Key failures |
|---|---|---|---|---|---|---|
| POST | `/api/challenges/generate` | PLAYER | Generate personal AI options (one list, **no regenerate**) | `AiChallengeService.generatePersonal` | 200 | 400 not a member, 404 kingdom |
| POST | `/api/challenges/select` | PLAYER | Persist a chosen option + start it | `AiChallengeService.selectAndStart` | 201 | 400 invalid option / not member |
| GET | `/api/challenges?kingdomId={id}` | PLAYER | List persisted challenges (seasonal/lobby/selected) | `ChallengeService.listByKingdom` | 200 | 404 kingdom |
| GET | `/api/challenges/{challengeId}` | PLAYER | Challenge details | `ChallengeService.getChallenge` | 200 | 404 unknown |
| POST | `/api/challenges/lobby/{lobbyId}/generate` | PLAYER (host) | Generate AI options for a lobby | `AiChallengeService.generateForLobby` | 200 | 403 not host, 400 lobby state, 404 |
| POST | `/api/challenges/lobby/{lobbyId}/attach` | PLAYER (host) | Persist + attach chosen option to the lobby | `AiChallengeService.attachToLobby` | 201 | 403 not host, 404 |
| POST | `/api/challenges` *(optional, emergency)* | ADMIN | Manual create fallback — **not** the product flow | `ChallengeService.createChallenge` | 201 | 404 kingdom, 400 |

**POST /api/challenges/generate**
```jsonc
// request — GenerateChallengeRequest
{ "playerId":1, "kingdomId":1, "period":"DAILY" }   // no custom prompt / regenerate in the normal flow
// 200 — ApiResponse<AiChallengeOptionsResponse>
{ "success":true, "message":"Generated", "data":{
  "kingdomId":1, "period":"DAILY", "division":"D3", "options":[
    { "title":"Walk 7,000 steps today", "description":"Reach 7,000 steps before midnight.",
      "kingdomId":1, "period":"DAILY", "difficulty":"EASY", "xpReward":150,
      "verificationType":"MOCK_FITNESS", "metricKey":"steps", "targetValue":7000, "safetyNotes":null },
    { "title":"20-minute brisk walk", "description":"...", "kingdomId":1, "period":"DAILY",
      "difficulty":"EASY", "xpReward":150, "verificationType":"MOCK_FITNESS",
      "metricKey":"minutes", "targetValue":20, "safetyNotes":null } ] } }
```
**No regenerate / no AI-prompt controls** in the normal flow — the player simply picks one of the returned options. (Custom prompts exist only in **private** lobbies.)

**POST /api/challenges/select**
```jsonc
// request — SelectChallengeRequest (stateless: client returns the chosen option)
{ "playerId":1, "option":{ "title":"Walk 7,000 steps today", "description":"...", "kingdomId":1,
  "period":"DAILY", "difficulty":"EASY", "xpReward":150, "verificationType":"MOCK_FITNESS",
  "metricKey":"steps", "targetValue":7000 } }
// 201 — ApiResponse<ChallengeProgressResponse> (Challenge persisted aiGenerated=true, run JOINED)
{ "success":true, "message":"Challenge started", "data":{
  "progressId":55, "challengeId":7, "challengeTitle":"Walk 7,000 steps today", "membershipId":10,
  "playerId":1, "kingdomId":1, "status":"JOINED", "attempts":0, "xpEarned":0, "joinedAt":"..." } }
```
**Server-side:** the option is re-validated/clamped (anti-tamper) before persisting — never trust client `xpReward`/`targetValue` blindly.

> `metricKey`/`targetValue`/`safetyNotes`/`active` now **persist on `Challenge`** (patch applied). On select/attach they are stored; verification reads the **stored** `targetValue` (never the client's value); `active=false` challenges can't be started and are hidden from active-only listings.

---

## 4. Submission APIs — `/api/submissions` (Member 2)

> "Submission" = a `ChallengeProgress` run.

| Method | URL | Role | Purpose | Service method | Success | Key failures |
|---|---|---|---|---|---|---|
| POST | `/api/submissions/start` | PLAYER | Start (join) a challenge | `SubmissionService.startChallenge` | 201 | 409 already started, 400 not kingdom member, 404 challenge |
| POST | `/api/submissions/{progressId}/submit` | PLAYER | Submit activity/proof | `SubmissionService.submit` | 200 | 400 bad state, 404 |
| GET | `/api/submissions?playerId={id}` | PLAYER | My active runs | `SubmissionService.listByPlayer` | 200 | 404 player |
| GET | `/api/submissions/{progressId}` | PLAYER | One run status | `SubmissionService.getProgress` | 200 | 404 |

**POST /api/submissions/start**
```jsonc
{ "playerId":1, "challengeId":7 }                 // StartChallengeRequest
// 201 — ApiResponse<ChallengeProgressResponse>
{ "success":true, "message":"Challenge started", "data":{
  "progressId":55, "challengeId":7, "challengeTitle":"Run 5km", "membershipId":10,
  "playerId":1, "kingdomId":1, "progress":0, "status":"JOINED", "attempts":0,
  "rejectionReason":null, "verifiedBy":null, "xpEarned":0,
  "joinedAt":"...", "submittedAt":null, "verifiedAt":null } }
```

**POST /api/submissions/{progressId}/submit**
```jsonc
{ "rawValue":"5.2km in 28min", "proofUrl":null, "metricValue":5.2 }   // SubmitProofRequest
// 200 data: same ChallengeProgressResponse with status:"SUBMITTED", submittedAt set
```

---

## 5. Verification APIs — `/api/verifications` (Member 2)

| Method | URL | Role | Purpose | Service method | Success | Key failures |
|---|---|---|---|---|---|---|
| POST | `/api/verifications/fitness/mock` | PLAYER¹ | Mock fitness auto-verify + award | `VerificationService.verifyByMockFitness` | 200 | 422 target not met, 404, idempotent no-op |
| GET | `/api/verifications/{progressId}` | PLAYER | Run verification state | `VerificationService.getStatus` | 200 | 404 |

¹ represents the provider webhook; dev/test only.

**POST /api/verifications/fitness/mock**
```jsonc
{ "progressId":55, "source":"MOCK_FITNESS", "externalId":"wk_2026_06_16_1", "metricValue":5.2 }
// 200 — ApiResponse<VerificationResultResponse>  (target met → awarded)
{ "success":true, "message":"Verified", "data":{
  "progressId":55, "status":"VERIFIED", "verifiedBy":"API", "completionRate":100,
  "awardedXp":500, "awardedSeasonalPoints":500, "awardedTotalPoints":500,
  "newXp":500, "newSeasonalPoints":500, "newTotalPoints":500, "division":"D3",
  "message":"5.2km ≥ 5km target — 100% complete" } }
// partial example: verifiedValue 60000 vs target 150000 → completionRate 40, awarded = 40% of each reward
// 422 — VerificationFailedException → ApiError, ChallengeProgress.attempts++ , status REJECTED, no XP
```
**Validation:** `progressId @NotNull`, `externalId @NotBlank`, `metricValue @NotNull`.

---

## 6. XP / Progress APIs — `/api/progress` (Member 3)

| Method | URL | Role | Purpose | Service method | Success | Key failures |
|---|---|---|---|---|---|---|
| GET | `/api/progress/players/{playerId}` | PLAYER/ADMIN | All-kingdom progress summary | `ProgressService.getPlayerProgress` | 200 | 404 player |
| GET | `/api/progress/players/{playerId}/kingdoms/{kingdomId}` | PLAYER/ADMIN | One kingdom progress + rank | `ProgressService.getKingdomProgress` | 200 | 404 |
| GET | `/api/progress/players/{playerId}/xp-history?kingdomId={id}` | PLAYER/ADMIN | XP ledger (transactions) | `ProgressService.getXpHistory` | 200 | 404 not a member |

**GET /api/progress/players/{playerId}/kingdoms/{kingdomId}**
```jsonc
// 200 — ApiResponse<KingdomProgressResponse>
{ "success":true, "message":"OK", "data":{
  "playerId":1, "kingdomId":1, "xp":12000, "level":4, "division":"D2",
  "totalPoints":12500, "allTimeTiles":125, "period":"WEEKLY",
  "seasonalPoints":1500, "seasonTiles":15, "seasonRank":3, "allTimeRank":3 } }
```
**GET .../xp-history**
```jsonc
// 200 — ApiResponse<List<ActivityRecordResponse>>
{ "success":true, "message":"OK", "data":[
  { "activityId":900, "membershipId":10, "challengeProgressId":55, "source":"MOCK_FITNESS",
    "externalId":"wk_...1", "rawValue":"5.2km", "xpAwarded":500, "status":"VERIFIED", "verifiedAt":"..." } ] }
```

---

## 7. Leaderboard APIs — `/api/leaderboards` (Member 3)

| Method | URL | Role | Purpose | Service method | Success | Key failures |
|---|---|---|---|---|---|---|
| GET | `/api/leaderboards/kingdoms/{kingdomId}?period={P}&division={D}` | PUBLIC | Division-scoped leaderboard | `LeaderboardService.getLeaderboard` | 200 | 404 kingdom, 400 bad enum |
| GET | `/api/leaderboards/kingdoms/{kingdomId}/king?period={P}` *(optional)* | PUBLIC | Current King/Queen (#1 of D1) | `LeaderboardService.getKing` | 200 | 404 |

**GET /api/leaderboards/kingdoms/1?period=WEEKLY&division=D1**
```jsonc
// 200 — ApiResponse<List<LeaderboardEntryResponse>>
{ "success":true, "message":"OK", "data":[
  { "rank":1, "playerId":3, "displayName":"Mona", "colorHex":"#1D3557", "xp":4200, "division":"D1" },
  { "rank":2, "playerId":1, "displayName":"Sara", "colorHex":"#E63946", "xp":3100, "division":"D1" } ] }
```
**Defaults:** `period=WEEKLY`; `division` optional (omit = all tiers, still ranked).

---

## 8. Admin APIs — `/api/admin` (Member 3) — **events + review** (not challenge creation)

> Admin does **not** create normal challenges. Admin = seasonal events, manual review, approve/reject.

| Method | URL | Role | Purpose | Service method | Success | Key failures |
|---|---|---|---|---|---|---|
| POST | `/api/admin/events` | ADMIN | Create/start a seasonal event (reuses `Lobby kind=SEASONAL`) | `AdminEventService.createEvent` | 201 | 404 kingdom, 400 invalid |
| GET | `/api/admin/submissions/pending` | ADMIN | Review queue (SUBMITTED/FLAGGED) | `AdminReviewService.listPending` | 200 | — |
| POST | `/api/admin/submissions/{progressId}/approve` | ADMIN | Approve → award XP | `AdminReviewService.approve` | 200 | 400 bad state, 404 |
| POST | `/api/admin/submissions/{progressId}/reject` | ADMIN | Reject with reason, no XP | `AdminReviewService.reject` | 200 | 400 bad state, 404 |
| POST | `/api/admin/challenges/{id}/disable` | ADMIN | Disable a bad AI challenge (`active=false`; history kept) | `AdminEventService.disableChallenge` | 200 | 404 |

**POST /api/admin/events**
```jsonc
// request — CreateEventRequest
{ "kingdomId":6, "title":"Ramadan Recitation", "description":"...", "period":"MONTHLY",
  "difficulty":"MEDIUM", "xpReward":1000, "startsAt":"2027-02-18T00:00:00", "endsAt":"2027-03-18T00:00:00" }
// 201 — ApiResponse<EventResponse>
{ "success":true, "message":"Event created", "data":{
  "lobbyId":9, "challengeId":21, "kingdomId":6, "kind":"SEASONAL",
  "startsAt":"2027-02-18T00:00:00", "endsAt":"2027-03-18T00:00:00" } }
```

**POST /api/admin/submissions/{progressId}/approve**
```jsonc
{ "note":"verified photo" }                        // ApproveSubmissionRequest (optional)
// 200 — ApiResponse<ChallengeProgressResponse> with status:"VERIFIED", verifiedBy:"ADMIN", xpEarned set
```
**POST /api/admin/submissions/{progressId}/reject**
```jsonc
{ "rejectionReason":"FLAGGED", "note":"impossible pace" }   // RejectSubmissionRequest
// 200 — data: ChallengeProgressResponse status:"REJECTED", rejectionReason:"FLAGGED"
```
**Validation:** `rejectionReason @NotNull` enum; run must be in a reviewable state.

---

## 9. Notifications — `/api/notifications` (Member 3, **optional**)

| Method | URL | Role | Purpose | Service method | Success |
|---|---|---|---|---|---|
| GET | `/api/notifications?playerId={id}` | PLAYER | List notifications | `NotificationService.list` | 200 |
| POST | `/api/notifications/{id}/read` | PLAYER | Mark read | `NotificationService.markRead` | 200 |

```jsonc
// 200 — ApiResponse<List<NotificationResponse>>
{ "success":true, "message":"OK", "data":[
  { "id":1, "type":"RESULT", "title":"Challenge approved", "body":"+500 XP in Sports",
    "read":false, "linkRef":"/progress/1/1", "createdAt":"..." } ] }
```

---

## 10. Endpoint count by module

| Module | Base path | Member | Endpoints (required) |
|---|---|---|---|
| User | `/api/users` | M1 | 5 |
| Kingdom | `/api/kingdoms` | M1 | 3 |
| Lobby | `/api/lobbies` | M1 | 4 |
| Challenge (AI) | `/api/challenges` | M2 | 6 (+1 optional admin create) |
| Submission | `/api/submissions` | M2 | 4 |
| Verification | `/api/verifications` | M2 | 2 |
| Progress | `/api/progress` | M3 | 3 |
| Leaderboard | `/api/leaderboards` | M3 | 1 (+1 optional king) |
| Admin (events+review) | `/api/admin` | M3 | 4 (+1 disable, now buildable) |
| Notifications | `/api/notifications` | M3 | 2 (optional) |

**Required total ≈ 32 endpoints** (M1 = 12, M2 = 12, M3 = 8), plus ~7 optional.

---

## 11. Request / response DTO ownership

DTOs live in `dto/request` and `dto/response`. **Each member creates only the DTOs for their own flow.**
A few response DTOs are *consumed* across flows — they are **owned (created) by the entity owner** and only
read by others (never modified). This is the resolution of the "DTOs per own flow" rule vs. cross-flow reuse.

| DTO | Type | Owner | Also used by |
|---|---|---|---|
| `CreatePlayerRequest`, `CreateAdminRequest`, `UpdatePlayerRequest`, `JoinKingdomRequest`, `CreateLobbyRequest`, `JoinLobbyRequest` | req | M1 | — |
| `PlayerProfileResponse`, `KingdomResponse`, `MembershipResponse`, `LobbyResponse`, `LobbyMemberResponse` | resp | M1 | M2/M3 read `LobbyResponse`; M3 reads membership view |
| `GenerateChallengeRequest`, `SelectChallengeRequest`, `GenerateLobbyChallengeRequest`, `AttachLobbyChallengeRequest`, `StartChallengeRequest`, `SubmitProofRequest`, `MockFitnessRequest`, `CreateChallengeRequest` (admin opt.) | req | M2 | — |
| `AiChallengeOption`, `AiChallengeOptionsResponse`, `ChallengeResponse`, `ChallengeProgressResponse`, `VerificationResultResponse` | resp | M2 | **M3** (admin queue/approve return `ChallengeProgressResponse`) |
| `ApproveSubmissionRequest`, `RejectSubmissionRequest`, `CreateEventRequest` | req | M3 | — |
| `ProgressSummaryResponse`, `KingdomProgressResponse`, `LeaderboardEntryResponse`, `ActivityRecordResponse`, `EventResponse`, `NotificationResponse` | resp | M3 | **M2** (reads `ActivityRecordResponse` if needed) |

---

## 12. Missing / recommended repository finders

> Per instructions: **listed, not added.** Do not modify repositories until the team agrees.

**✅ Added in the foundation patch (required):**
1. `ChallengeProgressRepository.findAllByStatus(ProgressStatus status)` + `findAllByStatusIn(Collection<ProgressStatus>)` — admin review queue (C8), incl. `FLAGGED`.
2. `ActivityRecordRepository.existsByChallengeProgressId(Integer)` + `findByChallengeProgressId(Integer)` — per-run XP dedup + traceability.

**Recommended (works without, but avoids N+1 queries — still not added):**
3. `ChallengeProgressRepository.findAllByMembershipIdIn(Collection<Integer> membershipIds)` — "my active runs" across a player's kingdoms in one query (`/api/submissions?playerId=`).
4. `PeriodScoreRepository.findAllByMembershipIdInAndPeriodAndPeriodStart(...)` — a kingdom's period leaderboard in one query.
5. `KingdomMembershipRepository.findAllByKingdomIdAndActiveTrue(Integer kingdomId)` — leaderboard excluding inactive memberships.

> `JpaRepository.findAllById(Iterable)` already exists — use it for batch-loading player display names (no new finder needed).

### ✅ Resolved model change (done in the foundation patch)
`ActivityRecord` now has a nullable `challengeProgressId` with `UNIQUE(challengeProgressId)`. Challenge-based XP
is traceable to its exact run and can't be awarded twice. `challengeProgressId` is **required** for challenge XP,
**null** for future non-challenge sources, and **only `RewardService` writes these rows** (verification / admin-review
services must call `RewardService`, never write XP/points directly).

### ✅ Applied model change (3-currency + competition model)
`KingdomMembership.totalPoints`; `PeriodScore.xp → seasonalPoints` (+ `getSeasonTiles()`); `getTilesOwned()` from `totalPoints`.
`Challenge.seasonalPointsReward`/`totalPointsReward`; `ChallengeProgress.challengeStartAt`/`challengeEndAt`/`verifiedValue`/`completionRate`/`seasonalPointsEarned`/`totalPointsEarned`; `ActivityRecord.seasonalPointsAwarded`/`totalPointsAwarded`; `Lobby.division`; `LobbyInvite.inviteCode`.
Enums: `KingdomType.SPECIAL_EVENTS → OPEN_CHALLENGE`, `InviteChannel +SMS`, `InviteStatus +EXPIRED`. Service `XpService → RewardService`. Verified `mvn compile`.

### ✅ Applied model change (approved)
`Challenge` now has `metricKey`, `targetValue`, `safetyNotes` (nullable) + `active` (Boolean, default `true`).
`ChallengeRepository` added `findAllByKingdomIdAndActiveTrue(kingdomId)` and
`findAllByKingdomIdAndPeriodAndActiveTrue(kingdomId, period)`. Verification compares the actual value against the
**stored** `targetValue` (the client never controls it); `active=false` disables a challenge without deleting its
history. Detail in `Flow-Contracts.md` §5.
