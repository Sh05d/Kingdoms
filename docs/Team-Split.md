# Team Split — Kingdom backend (Phase 3)

> ⚠ **Superseded for ownership by [`Final-Team-Split.md`](Final-Team-Split.md)** (AI-first correction: challenges
> are **AI-generated**, admin = **events + review** (not the challenge creator), and **lobbies** move into Member 1).
> This file's shared-file rules and golden rules still apply; where it conflicts on *who builds challenges* or the
> member areas, `Final-Team-Split.md` wins. See also `Flow-Ownership-Matrix.md`.

Exact ownership for 3 members. Goal: **everyone builds in parallel without editing the same file.**

**Shared, read-only for everyone (do NOT modify):** `model/*`, `enums/*`, `repository/*`,
`api/ApiResponse`, `api/ApiException`, `exception/*`, `advice/GlobalExceptionHandler`, `config/SecurityConfig`.
Repositories may only change if the team agrees a finder is missing (see `API-Contracts.md` §12) — propose first.

**Golden rules**
1. One writer per entity (see the matrix in `Flow-Contracts.md` §0). The only shared-write entities are
   `KingdomMembership` (M1 creates, M3 updates `xp`) and `ChallengeProgress` (M2 only; M3 goes through M2).
2. Each member creates **request DTOs** only for their own flow. Response DTOs are owned by the entity owner
   (`API-Contracts.md` §11); others read them, never edit them.
3. New files only inside your own package folders. Never rename/move a shared file.
4. **Controller style:** every controller returns `ResponseEntity<?>` wrapping `com.kingdom.API.ApiResponse`; controllers call services only (no repos/entities/logic), use `@Valid` request DTOs + response DTOs, and let `GlobalExceptionHandler` map errors. Full conventions in `Foundation-Summary.md` §7.

---

## Member 1 — User + Kingdom

**May create (new files):**
- `controller/UserController.java`, `controller/KingdomController.java`
- `service/UserService.java`, `service/PlayerService.java`, `service/MembershipService.java`
- `config/KingdomCatalogSeeder.java` *(seeds the 10 kingdoms — separate file from M3's demo seeder)*
- `dto/request/`: `CreatePlayerRequest`, `CreateAdminRequest`, `UpdatePlayerRequest`, `JoinKingdomRequest`
- `dto/response/`: `PlayerProfileResponse`, `KingdomResponse`, `MembershipResponse`

**Entities used:** `User`, `Player`, `Kingdom`, `KingdomMembership` (create + read), `Subscription` (read).
**Repositories used:** `UserRepository`, `PlayerRepository`, `KingdomRepository`, `KingdomMembershipRepository`, `SubscriptionRepository`.
**Services to create:** `UserService`, `PlayerService`, `MembershipService`.
**Controllers to create:** `UserController`, `KingdomController`.
**Endpoints owned:** all `/api/users/**` and `/api/kingdoms/**` (Flow A1–A7).

**Must NOT touch:** `Challenge*`, `ActivityRecord`, `PeriodScore`, `ChallengeProgress`; M2/M3 services & controllers; the `xp`/`level` fields of `KingdomMembership` (M3 owns XP mutation — M1 only sets join-time defaults).

**Merge-conflict risks:**
- `KingdomMembership` is shared-write with M3 → **agree the field split**: M1 writes `playerId,kingdomId,active,level=1,joinedAt` on join; M3 writes `xp,level` on award. Same repo, different fields, but coordinate on `level`.
- `config` folder also hosts M3's demo seeder → keep **two separate seeder files**, never one shared file.
- `MembershipResponse` is read by M3's progress views → freeze its shape early.

---

## Member 2 — Challenge + Submission + Verification

**May create (new files):**
- `controller/ChallengeController.java`, `controller/SubmissionController.java`, `controller/VerificationController.java`
- `service/ChallengeService.java`, `service/SubmissionService.java`, `service/VerificationService.java`
- `client/MockFitnessApiClient.java` *(in `client` package)* — optionally an interface `verification/VerificationProvider.java`
- `dto/request/`: `CreateChallengeRequest`, `StartChallengeRequest`, `SubmitProofRequest`, `MockFitnessRequest`
- `dto/response/`: `ChallengeResponse`, `ChallengeProgressResponse`, `VerificationResultResponse`

**Entities used:** `Challenge` (write), `ChallengeProgress` (write), `KingdomMembership` (**read** to resolve membershipId), `ActivityRecord` (**read** for history).
**Repositories used:** `ChallengeRepository`, `ChallengeProgressRepository`, `KingdomMembershipRepository` (read), `KingdomRepository` (read), `ActivityRecordRepository` (read).
**Services to create:** `ChallengeService`, `SubmissionService`, `VerificationService`.
**Controllers to create:** `ChallengeController`, `SubmissionController`, `VerificationController`.
**Endpoints owned:** all `/api/challenges/**`, `/api/submissions/**`, `/api/verifications/**` (Flow B1–B7).

**Must NOT touch:** `User`/`Player`/`Kingdom` writes; `ActivityRecord`/`PeriodScore` **writes** (M3 owns the ledger); `KingdomMembership` writes (read only); M1/M3 services & controllers.

**Cross-flow dependencies (build against the agreed interface, stub until ready):**
- On API verify success, call `RewardService.awardReward(membershipId, xpReward, source, externalId, challengeId)` (M3).
- Expose `SubmissionService.markApproved(progressId, by)` and `markRejected(progressId, reason)` for M3's admin review to call.

**Merge-conflict risks:**
- `ChallengeProgressResponse` is returned by M3's admin endpoints → freeze its shape early; M3 must not redefine it.
- `ChallengeProgress` is written only by M2, but M3 *calls* M2's mark* methods → agree those signatures up front.
- `client`/`verification` packages currently hold only `package-info.java` → add new files, don't edit the stubs' intent.

---

## Member 3 — XP + Leaderboard + Admin Review

**May create (new files):**
- `controller/ProgressController.java`, `controller/LeaderboardController.java`, `controller/AdminController.java`, *(optional)* `controller/NotificationController.java`
- `service/RewardService.java`, `service/ProgressService.java`, `service/LeaderboardService.java`, `service/AdminReviewService.java`, `service/NotificationService.java`
- `config/DemoDataSeeder.java` *(separate file from M1's kingdom seeder)*
- `dto/request/`: `ApproveSubmissionRequest`, `RejectSubmissionRequest`
- `dto/response/`: `ProgressSummaryResponse`, `KingdomProgressResponse`, `LeaderboardEntryResponse`, `ActivityRecordResponse`, *(optional)* `NotificationResponse`

**Entities used:** `ActivityRecord` (write — the ledger), `KingdomMembership` (**update `xp`/`level`**), `PeriodScore` (write), `Notification` (write), `Challenge`/`ChallengeProgress`/`Player`/`Kingdom` (read).
**Repositories used:** `ActivityRecordRepository`, `KingdomMembershipRepository` (update), `PeriodScoreRepository`, `NotificationRepository`, `ChallengeProgressRepository` (read), `ChallengeRepository` (read), `PlayerRepository` (read), `KingdomRepository` (read).
**Services to create:** `RewardService`, `ProgressService`, `LeaderboardService`, `AdminReviewService`, `NotificationService`.
**Controllers to create:** `ProgressController`, `LeaderboardController`, `AdminController`, *(optional)* `NotificationController`.
**Endpoints owned:** all `/api/progress/**`, `/api/leaderboards/**`, `/api/admin/**`, *(optional)* `/api/notifications/**` (Flow C1–C10).

**Must NOT touch:** `ChallengeProgress` **directly** (go through M2's `SubmissionService.markApproved/markRejected`); `Challenge` writes; `User`/`Player`/`Kingdom` writes; M1/M2 services & controllers.

**Cross-flow dependencies:**
- `RewardService.awardReward(...)` is called by both M2 (B6) and M3 (C6) — **define and freeze this signature first.**
- `AdminReviewService` calls M2's `SubmissionService.mark*` and M3's own `RewardService`/`NotificationService`.
- Needs the **missing finder** `ChallengeProgressRepository.findAllByStatus(...)` (propose to the team before adding).

**Merge-conflict risks:**
- `KingdomMembership` `xp`/`level` writes overlap with M1's create → field-split protocol (above).
- `config` demo seeder vs M1's kingdom seeder → two separate files.
- `ActivityRecordResponse` is read by M2 → freeze its shape early.

---

## Shared-file coordination summary

| Shared concern | Risk | Protocol |
|---|---|---|
| `KingdomMembership` | M1 create vs M3 `xp` update | Field split: M1 = identity/active/joinedAt; M3 = xp/level. Coordinate on `level`. |
| `ChallengeProgress` | M2 owns; M3 needs approve/reject | M3 calls `SubmissionService.markApproved/markRejected` — never writes directly. |
| `config/` seeders | two seeders, one folder | `KingdomCatalogSeeder` (M1) + `DemoDataSeeder` (M3) as separate files. |
| `dto/response/` shared DTOs | cross-flow reuse | Owner creates & freezes shape; others read only (`API-Contracts.md` §11). |
| `repository/` finders | someone adds a finder | Propose in `API-Contracts.md` §12; add by agreement; one PR, reviewed. |
| `RewardService.awardReward` signature | M2 + M3 both depend | Define interface first; M2 stubs until M3 delivers. |
