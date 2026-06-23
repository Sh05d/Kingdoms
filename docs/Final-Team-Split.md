# Final Team Split — Kingdom backend (Phase 3, AI-first)

**Authoritative ownership** for the corrected, AI-generated-challenge design. Supersedes the member sections
of `Team-Split.md`. Companion: `Flow-Ownership-Matrix.md` (grids), `Merge-Plan.md` (merge order),
`Flow-Contracts.md` / `API-Contracts.md` (flow + endpoint detail).

> **Core correction:** challenges are **AI-generated** (the player picks kingdom + duration; the system
> generates personalized challenges). **Admin is not the main challenge creator** — admin runs seasonal events
> and manual review. Lobbies move into MVP under Member 1.

**Shared, read-only for everyone (do NOT modify):** `model/*`, `enums/*`, `repository/*`, `api/ApiResponse`,
`api/ApiException`, `exception/*`, `advice/GlobalExceptionHandler`, `config/SecurityConfig`. Repositories change
only by team agreement (propose in `API-Contracts.md` §12). Controllers always return `ResponseEntity<?>` wrapping
`ApiResponse` and call services only (`Foundation-Summary.md` §7).

**Golden rules:** one writer per entity (matrix in `Flow-Ownership-Matrix.md`); each member creates only their own
**request** DTOs (response DTOs owned by the entity owner, read-only for others); new files only inside your own
package folders; freeze cross-called service signatures before coding (`Flow-Contracts.md` §4).

---

## Member 1 — User + Kingdom + **Lobby Foundation**

**Responsible for:** create player/admin · get profile · list kingdoms · join kingdom · membership/progress basics
· **create lobby · join lobby · view lobby details · prepare lobby state so AI lobby tasks can attach.**

**May create (new files):**
- `controller/UserController`, `controller/KingdomController`, `controller/LobbyController`
- `service/UserService`, `service/PlayerService`, `service/MembershipService`, `service/LobbyService`
- `config/KingdomCatalogSeeder` *(seeds the 10 kingdoms — separate file from M3's demo seeder)*
- `dto/request/`: `CreatePlayerRequest`, `CreateAdminRequest`, `UpdatePlayerRequest`, `JoinKingdomRequest`, `CreateLobbyRequest`, `JoinLobbyRequest`
- `dto/response/`: `PlayerProfileResponse`, `KingdomResponse`, `MembershipResponse`, `LobbyResponse`, `LobbyMemberResponse`

**Entities (write):** `User`, `Player`, `Kingdom`, `KingdomMembership` (create only), `Lobby`, `LobbyMember`, `LobbyInvite`.
**Entities (read):** `Subscription` (premium gate), `Challenge` (lobby's attached challenge).
**Repositories:** `User/Player/Kingdom/KingdomMembership/Subscription/Lobby/LobbyMember/LobbyInvite Repository`, `ChallengeRepository` (read).
**Services:** `UserService`, `PlayerService`, `MembershipService`, `LobbyService`.
**Controllers:** `UserController`, `KingdomController`, `LobbyController`.
**Endpoints owned:** all `/api/users/**`, `/api/kingdoms/**`, `/api/lobbies/**` (Flow A1–A11).

**Exposes for others:**
- `LobbyService.attachChallenge(lobbyId, challengeId)` — called by M2 (B10) to set `Lobby.challengeId`.
- `LobbyService.createSeasonalLobby(...)` — called by M3 (C-events) for seasonal events.

**Must NOT touch:** `Challenge`/`ChallengeProgress`/`ActivityRecord`/`PeriodScore`/`Notification` writes; the
`xp`/`level` fields of `KingdomMembership` (M3 owns XP mutation); M2/M3 services & controllers.

**Merge-conflict risks:** `KingdomMembership` shared-write with M3 (field split: M1 = identity/active/joinedAt,
M3 = xp/level); `Lobby.challengeId` set by M2 **through** `attachChallenge` (M1 stays sole writer); `config/`
also hosts M3's seeder → keep two separate seeder files; `LobbyResponse`/`MembershipResponse` read by M2/M3 → freeze early.

---

## Member 2 — **AI Challenge Generation** + Submission + Verification Engine *(hardest part)*

**Responsible for:** personal AI challenge generation (DAILY/WEEKLY/MONTHLY/YEARLY) · lobby challenge
generation/attach · **private-lobby custom-prompt** generation · select/start · submit · mock fitness verification · manual proof submission ·
verification attempt logging · progress status updates · **call `RewardService` after verification — never write XP directly.**

**May create (new files):**
- `controller/ChallengeController`, `controller/SubmissionController`, `controller/VerificationController`
- `service/AiChallengeService`, `service/ChallengeService`, `service/SubmissionService`, `service/VerificationService`
- `service/AiChallengeGenerator` *(interface / strategy)* + `service/MockAiChallengeGenerator` *(rule-based impl)*
  *(a future real-AI adapter implements `AiChallengeGenerator` in the `client` package — do not call a real paid AI API without approval)*
- `client/MockFitnessApiClient`; optionally `verification/VerificationProvider`
- `dto/request/`: `GenerateChallengeRequest`, `SelectChallengeRequest`, `GenerateLobbyChallengeRequest`, `AttachLobbyChallengeRequest`, `StartChallengeRequest`, `SubmitProofRequest`, `MockFitnessRequest`, `CreateChallengeRequest` *(admin emergency, optional)*
- `dto/response/`: `AiChallengeOption`, `AiChallengeOptionsResponse`, `ChallengeResponse`, `ChallengeProgressResponse`, `VerificationResultResponse`

**Entities (write):** `Challenge` (AI-generated main path; admin emergency via same service), `ChallengeProgress`.
**Entities (read):** `KingdomMembership` (division), `Kingdom`, `Lobby` (lobby gen), `ActivityRecord` (history).
**Repositories:** `Challenge/ChallengeProgress Repository`; read `KingdomMembership/Kingdom/Lobby/ActivityRecord Repository`.
**Services:** `AiChallengeService`, `ChallengeService`, `SubmissionService`, `VerificationService` (+ generator).
**Controllers:** `ChallengeController`, `SubmissionController`, `VerificationController`.
**Endpoints owned:** all `/api/challenges/**` (generate/select/list/details/lobby-generate/lobby-attach + optional admin create), `/api/submissions/**`, `/api/verifications/**` (Flow B0–B10).

**Calls into others:** `RewardService.awardReward(...)` (M3, on verify success); `SubmissionService.markApproved/markRejected`
exposed **for** M3; `LobbyService.attachChallenge(lobbyId, challengeId)` (M1, on B10).

**Must NOT touch:** `User`/`Player`/`Kingdom`/`Lobby` writes; `ActivityRecord`/`PeriodScore` writes (M3 owns the
ledger); `KingdomMembership` writes (read only); M1/M3 services & controllers.

**Merge-conflict risks:** `ChallengeProgressResponse` returned by M3's admin endpoints → freeze its shape;
`AiChallengeOption` maps to the now-applied `Challenge` fields `metricKey`/`targetValue`/`safetyNotes`/`active` (`Flow-Contracts.md` §5);
generator must stay behind the `AiChallengeGenerator` interface so the real-AI swap doesn't ripple.

---

## Member 3 — XP + Leaderboard + **Admin Events / Review**

**Responsible for:** `RewardService` · XP awarding · duplicate-XP prevention · `ActivityRecord` creation ·
`KingdomMembership` XP updates · division calculation · progress summary · leaderboard · **admin seasonal event
creation/start · admin manual review · approve/reject** · notifications (optional) · demo data (later).

**May create (new files):**
- `controller/ProgressController`, `controller/LeaderboardController`, `controller/AdminController`, *(opt.)* `controller/NotificationController`
- `service/RewardService`, `service/ProgressService`, `service/LeaderboardService`, `service/AdminReviewService`, `service/AdminEventService`, `service/NotificationService`
- `config/DemoDataSeeder` *(separate file from M1's kingdom seeder)*
- `dto/request/`: `ApproveSubmissionRequest`, `RejectSubmissionRequest`, `CreateEventRequest`
- `dto/response/`: `ProgressSummaryResponse`, `KingdomProgressResponse`, `LeaderboardEntryResponse`, `ActivityRecordResponse`, `EventResponse`, *(opt.)* `NotificationResponse`

**Entities (write):** `ActivityRecord` (reward ledger: xp/seasonal/total), `KingdomMembership` (`xp`/`level`/`totalPoints`), `PeriodScore` (`seasonalPoints`), `Notification`. **`RewardService`** awards all 3 currencies, scaled by `completionRate`; public-lobby awards pass 0 seasonal/total, private-lobby awards nothing.
**Entities (read):** `ChallengeProgress`, `Challenge`, `Player`, `Kingdom`, `Lobby`.
**Repositories:** `ActivityRecord/KingdomMembership/PeriodScore/Notification Repository`; read `ChallengeProgress/Challenge/Player/Kingdom Repository`.
**Services:** `RewardService`, `ProgressService`, `LeaderboardService`, `AdminReviewService`, `AdminEventService`, `NotificationService`.
**Controllers:** `ProgressController`, `LeaderboardController`, `AdminController` (+ optional Notification).
**Endpoints owned:** all `/api/progress/**`, `/api/leaderboards/**`, `/api/admin/**` (events + review), *(opt.)* `/api/notifications/**` (Flow C1–C10 + C-events).

**Calls into others:** `SubmissionService.markApproved/markRejected` (M2, on admin review); `ChallengeService.createChallenge`
(M2, for seasonal event challenge); `LobbyService.createSeasonalLobby` (M1, for the event lobby).

**Must NOT touch:** `ChallengeProgress` directly (go through M2's mark* methods); `Challenge`/`Lobby` writes
(go through M2/M1 services); `User`/`Player`/`Kingdom` writes; M1/M2 services & controllers.

**Merge-conflict risks:** `KingdomMembership` xp/level writes overlap M1's create (field-split protocol);
`config/` demo seeder vs M1's kingdom seeder (two files); `ActivityRecordResponse` read by M2 → freeze early;
seasonal events span 3 members (M3 orchestrates, M2 makes the challenge, M1 makes the lobby) → use the agreed interfaces.

---

## Admin role — corrected (MVP)

Admin is **not** the main challenge creator. MVP admin responsibilities:
1. **Seasonal event creation/start** — `POST /api/admin/events` (reuses `Lobby kind=SEASONAL`, `createdByAdmin=true`).
2. **Manual proof review** — `GET /api/admin/submissions/pending`.
3. **Approve / reject suspicious submissions** — `POST /api/admin/submissions/{id}/approve|reject`.

Optional / later: disable/report a bad AI challenge (`POST /api/admin/challenges/{id}/disable`) — **buildable now**
(`Challenge.active` added; `AdminEventService.disableChallenge` → M2's `ChallengeService.setActive`, history kept);
manage event settings; moderation dashboards.
The optional admin **emergency manual challenge create** (`POST /api/challenges`, ADMIN) is a fallback only.
