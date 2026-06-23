# Implementation Order — Kingdom backend (Phase 3)

A safe sequence so each flow has what it depends on before it's built. Other flows need **users** and
**kingdoms** to exist first, so Member 1 goes first; XP/admin needs challenges/submissions, so Member 3 goes last.

> Rule at every step: `mvn compile` must stay green, and the new endpoints must be testable in Postman with
> the dev shortcut (explicit `playerId`/`userId`, security still `permitAll`).

---

## Step 1 — Member 1: User + Kingdom + Lobby Foundation *(foundation of all flows)*

**Implement**
- `KingdomCatalogSeeder` (seed the 10 kingdoms with type/name/nameAr/premiumOnly/verificationSource).
- `UserService` (createPlayer, createAdmin), `PlayerService` (getProfile, [update]), `MembershipService` (joinKingdom, listMemberships, getMembership).
- `LobbyService` (createLobby, joinLobby, getLobby, listPublicLobbies; **plus** `attachChallenge`/`createSeasonalLobby` for M2/M3).
- `UserController`, `KingdomController`, `LobbyController` + M1 DTOs.

**Test**
- Unit/slice: join rules — duplicate join → 409; free cap (3rd kingdom) → blocked; premiumOnly kingdom blocked for free.
- Repository sanity: `@DataJpaTest` on `KingdomMembershipRepository.countByPlayerIdAndActiveTrue`, `findByPlayerIdAndKingdomId`.

**Must compile before moving on**
- Whole project compiles; app boots; 10 kingdoms present on startup.

**Postman**
- `POST /api/users` (create 2–3 players) → note `playerId`s.
- `POST /api/users/admin` → note admin.
- `GET /api/kingdoms`, `GET /api/kingdoms/{id}`.
- `POST /api/kingdoms/{id}/join` (happy path + duplicate + cap).
- `GET /api/users/{playerId}/memberships`.
- `POST /api/lobbies` (premium; public lobby `division` locked to host) → `GET /api/lobbies/{id}` → `POST /api/lobbies/{id}/join` (same-division enforced for public).

✅ **Exit criteria:** players + admin exist, kingdoms seeded, a player can join a kingdom and read membership, and create/join a lobby.

---

## Step 2 — Member 2: AI Challenge Generation + Submission + Verification *(needs Step 1)*

**Implement**
- `AiChallengeService` + `AiChallengeGenerator`/`MockAiChallengeGenerator` (personal + lobby generation, select/persist; **options list only, no user-facing regenerate**; private-lobby custom prompt) + `ChallengeService` (listByKingdom, getChallenge; optional admin emergency create) + `ChallengeController`.
- `SubmissionService` (startChallenge, submit, listByPlayer, getProgress; **plus** `markApproved`/`markRejected` for M3) + `SubmissionController`.
- `MockFitnessApiClient` + `VerificationService` (verifyByMockFitness, getStatus) + `VerificationController`.
- **Stub** `RewardService.awardReward(...)` to an agreed interface (returns a fake result) until Step 3 — verification can mark VERIFIED and call the stub.

**Test**
- Start rules: start before joining kingdom → blocked; second start of same challenge → 409.
- Verify rules: metric ≥ target → VERIFIED (+ awardReward called once); metric < target → 422, `attempts++`, no XP.
- Idempotency: same `source+externalId` twice → single award (assert via stub call count / `existsBySourceAndExternalId`).

**Must compile before moving on**
- Project compiles with the `RewardService` **interface** present (stub impl OK); no direct writes to `ActivityRecord`/`PeriodScore` yet.

**Postman**
- `POST /api/challenges/generate` (Sports, DAILY) → options; `POST /api/challenges/select` → note `progressId`/`challengeId`. *(Admin `POST /api/challenges` is an optional fallback, not the main flow.)*
- `GET /api/challenges?kingdomId=1`, `GET /api/challenges/{id}`.
- (Lobby) `POST /api/challenges/lobby/{lobbyId}/generate` → `POST /api/challenges/lobby/{lobbyId}/attach` (sets `Lobby.challengeId`).
- `POST /api/submissions/start` → `progressId`; `POST /api/submissions/{progressId}/submit`.
- `POST /api/verifications/fitness/mock` (pass and fail cases); `GET /api/verifications/{progressId}`.
- `GET /api/submissions?playerId={id}`.

✅ **Exit criteria:** a player can AI-generate → select → start → submit → auto-verify a Sports challenge; lobby challenge generates + attaches; failed attempts logged; XP award call wired to the stub.

---

## Step 3 — Member 3: XP + Leaderboard + Admin Events / Review *(needs Steps 1–2)*

**Implement**
- `RewardService.awardReward(...)` **real** impl: idempotent `ActivityRecord` (xp/seasonal/total) + `membership.xp`+`totalPoints` + active `PeriodScore.seasonalPoints`, all scaled by `completionRate` — replaces M2's stub.
- `ProgressService` (getPlayerProgress, getKingdomProgress, getXpHistory) + `ProgressController`.
- `LeaderboardService` (getLeaderboard division-scoped, [getKing]) + `LeaderboardController`.
- `AdminReviewService` (listPending, approve, reject — calls M2's `mark*` + `RewardService` + notifications) and `AdminEventService` (createEvent — calls M2's `ChallengeService` + M1's `LobbyService.createSeasonalLobby`) + `AdminController`.
- `NotificationService` *(optional)*; `DemoDataSeeder` (sample players/AI challenges/verified activity).
- `ChallengeProgressRepository.findAllByStatus(...)` already present (foundation patch).

**Test**
- Award: VERIFIED adds `xp`+`totalPoints` to membership **and** `seasonalPoints` to PeriodScore; partial (verifiedValue<target) → pro-rated; division re-derives from xp at thresholds (9,999→D3, 10,000→D2, 25,000→D1); all-time tiles = totalPoints/100, season tiles = seasonalPoints/100.
- Idempotency: awardReward twice with same key → one ActivityRecord, xp added once.
- Leaderboard: division scoping (D1 list excludes D2/D3); rank order by xp desc.
- Admin: approve manual SUBMITTED → VERIFIED + XP; reject → REJECTED + reason, no XP; re-approve → no double award.

**Must compile before moving on**
- Full project compiles with the real `RewardService`; M2's stub removed; end-to-end loop works.

**Postman**
- `POST /api/verifications/fitness/mock` (now awards real XP) → `GET /api/progress/players/{playerId}/kingdoms/1` shows xp/division.
- `GET /api/progress/players/{playerId}/xp-history?kingdomId=1`.
- `GET /api/leaderboards/kingdoms/1?period=WEEKLY&division=D3`.
- Manual flow: submit a manual challenge → `GET /api/admin/submissions/pending` → `POST /api/admin/submissions/{id}/approve` / `reject`.
- Admin event: `POST /api/admin/events` (e.g. a Faith seasonal event) → players join + complete it.

✅ **Exit criteria:** full verified-reward loop (join → **AI challenge** → start window → verify → **XP + seasonal + total points** scaled by completion → season + all-time leaderboards/tiles) works end-to-end for Sports, plus admin seasonal event + manual review.

---

## Step 4 — Final integration pass

**Implement / verify**
- Replace any remaining stubs; confirm the cross-service call graph (`Flow-Contracts.md` §4) is live.
- Consistency: one player runs the entire journey across all three flows; numbers reconcile (membership xp == sum of awarded ActivityRecords; leaderboard matches progress).
- Freeze shared response DTO shapes; remove dead/temporary fields.

**Test**
- One integration test that runs the whole loop on Sports.
- Re-run all module tests together; `mvn test` green.

**Postman**
- Full demo collection runs top-to-bottom against a fresh DB without manual fixups.

✅ **Exit criteria:** end-to-end Sports demo green; `mvn test` passes; no stubs left.

---

## Step 5 — Final security phase

**Implement**
- Replace `SecurityConfig` `permitAll` with real auth: self-hosted JWT register/login, BCrypt password hashing (`User.passwordHash` already exists), token returned on login.
- Role-based authorization: `ADMIN`-only on `/api/challenges` (POST), `/api/admin/**`, `/api/users/admin`; `PLAYER` on player actions; `PUBLIC` on list/details/register.
- Resolve identity from the **token**, not request fields — drop the dev `playerId`/`userId` body params (or validate them against the principal).

**Test**
- Player hitting an admin endpoint → 403; admin allowed; unauthenticated on protected route → 401.
- Login returns a usable token; protected routes require it.

**Must compile before moving on**
- Project compiles with security enforced; existing flows still pass when the correct role/token is supplied.

**Postman**
- Auth folder: register → login → copy token → authorized calls; negative tests (wrong role → 403).

✅ **Exit criteria:** no fake security left; players can't reach admin APIs; identity comes from auth.

---

## Step 6 — Final Postman / demo data phase

**Implement**
- `DemoDataSeeder` produces a believable Sports demo (several players across D1/D2/D3, challenges, verified activity, a leaderboard with a King).
- Finalize a Postman collection mirroring the demo script; document how to run secured calls (token setup).

**Test**
- Fresh DB + seeder + Postman collection → full happy-path demo runs unattended.

**Postman / demo**
- Ordered collection: register/login → join → challenge → verify → progress → leaderboard → admin review.
- Save environment variables (baseUrl, token, ids) so the panel demos cleanly.

✅ **Exit criteria:** one-click demo from a clean database; documented, secured, reproducible.

---

## Dependency summary

```
Step1 (M1: users, kingdoms)
   └─▶ Step2 (M2: challenges, submissions, verify — RewardService stubbed)
          └─▶ Step3 (M3: XP, leaderboard, admin — real RewardService)
                 └─▶ Step4 integration ─▶ Step5 security ─▶ Step6 demo data
```
Members 2 and 3 can **start scaffolding** (DTOs, controller skeletons, service signatures) in parallel once
Step 1's entities/seed exist — but their flows only go green in the order above.
