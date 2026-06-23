# Merge Plan — Kingdom backend (Phase 3, AI-first)

How three members merge into `main` without breaking each other. Pairs with `Implementation-Order.md`
(build sequence), `Final-Team-Split.md` (ownership), `Flow-Ownership-Matrix.md` (grids).

> The foundation (models, enums, repositories, `api`/`exception`/`advice`/`config`) is locked and compiling.
> Everything below is **new files inside owned packages** + the few coordinated shared points.

---

## 1. Branch strategy

- `main` — always compiles (`mvn compile`) and ideally `mvn test` green. Protected; merge via PR.
- `feat/m1-user-kingdom-lobby`, `feat/m2-ai-challenge-verify`, `feat/m3-xp-leaderboard-admin` — one per member.
- Rebase on `main` before each PR. Small, frequent PRs beat one giant merge.

---

## 2. Step 0 — freeze the contracts first (do this before any member writes a service)

Land these **shared, low-churn** pieces on `main` first so everyone compiles against stable signatures:

1. **Interface signatures** (empty interfaces / method stubs, no logic):
   - `RewardService.awardReward(...)`, `SubmissionService.markApproved/markRejected(...)`,
     `LobbyService.attachChallenge/createSeasonalLobby(...)`, `ChallengeService.createChallenge(...)`,
     `AiChallengeGenerator.generate(...)`, `NotificationService.create(...)`.
2. **Shared response DTO shapes** that cross flows: `MembershipResponse`, `LobbyResponse`,
   `ChallengeProgressResponse`, `ActivityRecordResponse`. Owner defines fields; consumers code against them.
3. **Repository finders** the team agreed to add (one small PR): the already-added `findAllByStatus(...)` /
   `findByChallengeProgressId(...)`; decide on the recommended efficiency finders (`API-Contracts.md` §12).
4. **Decision:** apply the recommended `Challenge` model patch (`metricKey`/`targetValue`/`safetyNotes`) **or**
   adopt the fallback (target in verify request). Land this **before M2 codes verification** — it changes
   `AiChallengeOption` persistence and B7. *(Currently NOT applied — awaiting approval.)*

Once Step 0 is on `main`, members merge in the dependency order below.

---

## 3. Merge order (matches Implementation-Order.md)

1. **Member 1 first.** Other flows need users + kingdoms (and lobby state). Merge M1 once create-player,
   join-kingdom, kingdom seed, and lobby create/join compile and pass.
2. **Member 2 second.** Needs M1's entities + `KingdomMembershipRepository`. Merge with `RewardService` **stubbed**
   to the frozen signature; AI generation + submission + mock verification working end-to-end against the stub.
3. **Member 3 third.** Implements the real `RewardService` (replaces M2's stub), progress, leaderboard, admin events
   & review. This PR also wires `AdminReviewService` → M2's `mark*` and `AdminEventService` → M1/M2 services.
4. **Integration PR.** Remove stubs; one end-to-end Sports run reconciles (membership.xp == Σ awarded ActivityRecords).

---

## 4. Conflict hotspots & how to avoid them

| Hotspot | Why it conflicts | Avoidance |
|---|---|---|
| `KingdomMembership` writes | M1 creates, M3 updates `xp`/`level` | Field split; M3 only ever touches `xp`/`level`. Never edit M1's create path. |
| `ChallengeProgress` writes | M2 owns; M3 needs approve/reject | M3 calls `SubmissionService.markApproved/markRejected` — no direct writes. |
| `Lobby.challengeId` | M2 generates the challenge; M1 owns Lobby | M2 calls `LobbyService.attachChallenge` — M1 stays sole writer. |
| `config/` seeders | M1 kingdom seed + M3 demo seed, same folder | Two separate files: `KingdomCatalogSeeder` (M1), `DemoDataSeeder` (M3). |
| Shared response DTOs | `ChallengeProgressResponse`/`ActivityRecordResponse`/`LobbyResponse` read across flows | Owner freezes shape in Step 0; consumers import, never edit. |
| `repository/` finders | two members add finders to one interface | All finder additions in the Step-0 PR, reviewed together. |
| `AiChallengeOption` fields | depend on the `Challenge` model patch | Resolve the Step-0 model decision before M2 codes select/verify. |
| Seasonal events | span M1 + M2 + M3 | M3 orchestrates only; the Lobby + Challenge writes go through M1/M2 services. |

**No two members edit the same file.** Controllers, services, and DTOs live in member-owned files; the only
shared touch-points are the Step-0 interfaces/DTOs/finders, landed once and then treated as read-only.

---

## 5. Integration checkpoints (gate each merge)

- **After M1:** `mvn compile` green; 10 kingdoms seeded; create player → join kingdom → create/join lobby works in Postman.
- **After M2:** start → submit → mock-verify works (XP via stub); AI generate → select → start works; lobby generate → attach sets `Lobby.challengeId`.
- **After M3:** real `RewardService` awards XP + updates membership/PeriodScore; leaderboard division-scoped; admin event create + manual approve/reject; idempotency holds (re-verify / re-approve = no double XP).
- **Integration:** one player completes the full AI loop end-to-end; numbers reconcile; `mvn test` green; no stubs remain.

---

## 6. Definition of "ready to merge" (per member)

- Compiles on a fresh `main` rebase; only files inside the member's owned packages changed (+ Step-0 shared, unchanged).
- Returns `ResponseEntity<?>` + `ApiResponse`; no raw entities; no business logic in controllers; throws shared exceptions.
- Cross-service calls use the frozen interfaces (stubs allowed pre-M3).
- Adds tests for its invariants (join caps, idempotent award, division thresholds, admin review state machine).
- No new writers introduced for an entity it doesn't own.
