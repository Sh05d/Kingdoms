# Foundation Summary — Kingdom backend (Phase 1 + 2)

The shared foundation the whole team builds on. **Models, enums, repositories, and shared
infrastructure only** — no services, controllers, or DTOs yet (those are Phase 3, flow-based).

- **Base package:** `com.kingdom`
- **Stack:** Spring Boot 4.0.6, Java 17, Maven, Lombok, Spring Data JPA, MySQL (Redis/realtime are roadmap infra, not modelled here).
- **Build status:** `mvn compile` → BUILD SUCCESS.
- **Source of truth:** `docs/Kingdom-Master-Spec.md`. Name reconciliation: `docs/Entity-Mapping.md`.

---

## 1. Final entity list (17) — `com.kingdom.Model`

| # | Entity | Table | Purpose |
|---|--------|-------|---------|
| 1 | `User` | `users` | Account: identity, role, password hash (self-hosted auth). |
| 2 | `Subscription` | `subscription` | Slim mirror of provider status (no Payment table). |
| 3 | `Player` | `player` | Game profile (1–1 with User); colour + prefs; no global XP. |
| 4 | `Kingdom` | `kingdom` | One of the 10 life-domain kingdoms. |
| 5 | `KingdomMembership` | `kingdom_membership` | **Bridge** Player↔Kingdom; per-kingdom `xp` (level/division) + `totalPoints` (all-time tiles/leaderboard); derives division & all-time tiles. |
| 6 | `ActivityRecord` | `activity_record` | Verified activity = the **reward ledger** (`xpAwarded`/`seasonalPointsAwarded`/`totalPointsAwarded`) + anti-cheat key; `challengeProgressId` ties a challenge award to its exact run. |
| 7 | `PeriodScore` | `period_score` | Current-season bucket — `seasonalPoints` (resets each season = period window); derives season tiles; rank computed. |
| 8 | `Challenge` | `challenge` | A challenge (SOLO/LOBBY). AI challenges set `aiGenerated` + `metricKey`/`targetValue`; `safetyNotes` for warnings; `active=false` disables without deleting. |
| 9 | `ChallengeProgress` | `challenge_progress` | One player's run: join→submit→verify→award/reject; tracking window (`challengeStartAt`/`challengeEndAt`), `verifiedValue`/`completionRate`, earned `xp`/`seasonal`/`total`. |
| 10 | `Badge` | `badge` | Badge catalog. |
| 11 | `PlayerBadge` | `player_badge` | A badge awarded to a player. |
| 12 | `HexTile` | `hex_tile` | One cell of a kingdom's shared hex grid (all-time owner). |
| 13 | `Lobby` | `lobby` | Competition (Premium-created); `division`-locked for public same-division joins; reused for admin events via `kind`. |
| 14 | `LobbyMember` | `lobby_member` | A participant in a lobby. |
| 15 | `LobbyInvite` | `lobby_invite` | Private-lobby invite by phone → **Twilio SMS**; `inviteCode` matches the reply webhook. |
| 16 | `ConnectedAccount` | `connected_account` | OAuth tokens for auto-verification. |
| 17 | `Notification` | `notification` | In-app notification. |

> Derived getters (never columns): `KingdomMembership.getDivision()` (from xp), `KingdomMembership.getTilesOwned()` (all-time = `totalPoints`/100), `PeriodScore.getSeasonTiles()` (season = `seasonalPoints`/100).

---

## 2. Final enum list (21) — `com.kingdom.Enums`

| Enum | Values |
|------|--------|
| `UserRole` | PLAYER, ADMIN |
| `SubscriptionPlan` | FREE, PREMIUM |
| `SubscriptionStatus` | ACTIVE, CANCELLED, EXPIRED, PAST_DUE |
| `KingdomType` | SPORTS, LEARNING, CHARITY, GAMING, VOLUNTEERING, FAITH, NUTRITION, READING, CREATOR, **OPEN_CHALLENGE** *(open-category, lobby-only, exempt from the free-2 limit)* |
| `Period` | DAILY, WEEKLY, MONTHLY, YEARLY |
| `Difficulty` | EASY, MEDIUM, HARD |
| `ChallengeScope` | SOLO, LOBBY |
| `VerificationStatus` | PENDING, VERIFIED, REJECTED |
| `ProgressStatus` | JOINED, IN_PROGRESS, SUBMITTED, VERIFIED, REJECTED, EXPIRED |
| `RejectionReason` | NOT_COMPLETED, UNVERIFIABLE, FLAGGED |
| `VerifierType` | API, AI, ADMIN |
| `BadgeType` | LEVEL, SEASONAL, KING, MILESTONE |
| `LobbyVisibility` | PUBLIC, PRIVATE |
| `LobbyStatus` | OPEN, ACTIVE, FINISHED, CANCELLED |
| `LobbyKind` | NORMAL, SPECIAL_EVENT, SEASONAL |
| `MemberRole` | HOST, MEMBER |
| `InviteChannel` | SMS *(Twilio — primary)*, WHATSAPP, EMAIL |
| `InviteStatus` | PENDING, ACCEPTED, REJECTED, EXPIRED |
| `ConnectedProvider` | APPLE_HEALTH, GOOGLE_FIT, STRAVA, GITHUB, STEAM, TWITCH, MOYASAR, QURAN_FOUNDATION, OPEN_FOOD_FACTS, GOOGLE_BOOKS, READWISE, YOUTUBE, BEHANCE, GEOAPIFY |
| `NotificationType` | INVITE, RESULT, LEVEL_UP, BADGE, SYSTEM |
| `Division` | D1, D2, D3 *(derived from XP — never stored)* |

---

## 3. Repository list (17) — `com.kingdom.Repository`

All extend `JpaRepository<Entity, Integer>`; only invariant-driven finders are added.

| Repository | Key finders beyond CRUD |
|---|---|
| `UserRepository` | `findByEmail`, `findByUsername`, `existsByEmail`, `existsByUsername` |
| `SubscriptionRepository` | `findAllByUserId`, `findByProviderRef` |
| `PlayerRepository` | `findByUserId`, `existsByColorHex` |
| `KingdomRepository` | `findByType` |
| `KingdomMembershipRepository` | `findByPlayerIdAndKingdomId`, `findAllByPlayerId`, `findAllByKingdomId`, `countByPlayerIdAndActiveTrue`, `countByPlayerIdAndActiveTrueAndKingdomIdNot`, `findAllByKingdomIdAndActiveTrue` |
| `ActivityRecordRepository` | `findBySourceAndExternalId`, `existsBySourceAndExternalId`, `existsByChallengeProgressId`, `findByChallengeProgressId`, `findAllByMembershipId` |
| `PeriodScoreRepository` | `findAllByMembershipId`, `findByMembershipIdAndPeriodAndPeriodStart`, `findAllByMembershipIdInAndPeriodAndPeriodStart` |
| `ChallengeRepository` | `findAllByKingdomId`, `findAllByKingdomIdAndScope`, `findAllByKingdomIdAndActiveTrue`, `findAllByKingdomIdAndPeriodAndActiveTrue` |
| `ChallengeProgressRepository` | `findAllByMembershipId`, `findAllByChallengeId`, `findByChallengeIdAndMembershipId`, `findAllByStatus`, `findAllByStatusIn`, `findAllByMembershipIdAndStatusIn` |
| `BadgeRepository` | `findAllByType`, `findByRuleKey` |
| `PlayerBadgeRepository` | `findAllByPlayerId` |
| `HexTileRepository` | `findAllByKingdomId`, `findAllByOwnerMembershipId` |
| `LobbyRepository` | `findAllByVisibilityAndStatus`, `findAllByHostPlayerId`, `findByInviteCode` |
| `LobbyMemberRepository` | `findAllByLobbyId`, `findByLobbyIdAndPlayerId` |
| `LobbyInviteRepository` | `findAllByLobbyId`, `findAllByInvitedPlayerId` |
| `ConnectedAccountRepository` | `findByPlayerIdAndProvider`, `findAllByPlayerId` |
| `NotificationRepository` | `findAllByPlayerId`, `findAllByPlayerIdAndReadFalse` |

---

## 4. Database unique constraints

**Composite (table-level `@UniqueConstraint`):**
| Entity | Constraint | Why |
|---|---|---|
| `KingdomMembership` | `UNIQUE(playerId, kingdomId)` | A player joins a kingdom only once. |
| `ActivityRecord` | `UNIQUE(source, externalId)` | **Anti-cheat idempotency** — a retried webhook can never double-count. |
| `ActivityRecord` | `UNIQUE(challengeProgressId)` *(nullable)* | **One reward (XP+points) award per challenge run** — prevents duplicate rewards; NULL allowed for non-challenge sources. |
| `ConnectedAccount` | `UNIQUE(playerId, provider)` | One link per provider per player. |
| `HexTile` | `UNIQUE(kingdomId, q, r)` | One tile per grid coordinate per kingdom. |

**Single-column (`@Column(unique = true)`):**
`User.email`, `User.username`, `Player.userId` (enforces 1–1 with User), `Player.colorHex`
(unique map colour), `Kingdom.type` (one row per kingdom type).

---

## 5. Important business invariants

1. **Three currencies, all per-kingdom.** `KingdomMembership.xp` → level/division (permanent, never resets). `KingdomMembership.totalPoints` → all-time tiles + all-time leaderboard (permanent). `PeriodScore.seasonalPoints` → current-season tiles + season leaderboard (**resets each season** = each period window). A player can be D1 in Sports and D3 in Reading at once. No global progression.
2. **Division is derived from XP, never stored.** `D1 ≥ 25,000 · D2 10,000–24,999 · D3 0–9,999` via `getDivision()`.
3. **Two tile maps; XP does not control tiles.** All-time tiles = `floor(totalPoints/100)` (`getTilesOwned()`); current-season tiles = `floor(seasonalPoints/100)` (`PeriodScore.getSeasonTiles()`). `HexTile.ownerMembershipId` is the all-time owner; the season map is computed live.
4. **Anti-cheat idempotency.** Every verified award is recorded once via `ActivityRecord` `UNIQUE(source, externalId)` **and** `UNIQUE(challengeProgressId)`.
5. **Failing never subtracts rewards.** A rejected submission yields nothing; it never reduces XP/points.
6. **Award on VERIFIED, scaled by completion.** `completionRate = min(100%, verifiedValue / targetValue)`; `xpAwarded = xpReward × rate`, `seasonalPointsAwarded = seasonalPointsReward × rate`, `totalPointsAwarded = totalPointsReward × rate`. The reward service adds these to `KingdomMembership.xp`, `KingdomMembership.totalPoints`, and the active `PeriodScore.seasonalPoints`, and writes one `ActivityRecord`.
7. **Only the reward service writes rewards.** Only **`RewardService`** writes XP / seasonal points / total points, creates challenge-based `ActivityRecord` rows, and updates `KingdomMembership` (`xp`, `totalPoints`) + `PeriodScore` (`seasonalPoints`). Verification and admin-review services compute completion and **call** `RewardService` — they never write rewards directly. Dedup via `existsByChallengeProgressId` / `existsBySourceAndExternalId`.
8. **Tracking window.** Only real-world activity between `ChallengeProgress.challengeStartAt` and `challengeEndAt` counts; nothing is tracked before the player starts.
9. **Partial rewards are backend-computed (metric challenges).** The client never sends completion % or the target; the backend computes `completionRate` from the **stored** `Challenge.targetValue`. Manual-proof challenges are admin-decided.
10. **Free cap = 2 NORMAL kingdoms; OPEN_CHALLENGE is exempt.** `countByPlayerIdAndActiveTrueAndKingdomIdNot(playerId, openChallengeKingdomId) < 2`. The **OPEN_CHALLENGE** kingdom doesn't count and is lobby-only; the 3rd+ normal kingdom needs Premium.
11. **One active challenge per kingdom (MVP).** Enforced on start via `findAllByMembershipIdAndStatusIn(membershipId, [JOINED, IN_PROGRESS, SUBMITTED])`.
12. **Lobbies (Premium-only to create).** Public lobby is **same-division** (locked via `Lobby.division`); reward = **XP only — NO seasonal/total points, so NO tiles and NO leaderboard impact** (XP still counts for level/division). Private lobby is **invite-only via Twilio SMS** and gives **no rewards** at all. **Lobby creation has no user-facing difficulty step** (difficulty is backend-internal); **challenge selection shows options only — no regenerate** (private custom prompt excepted). Public ≤ 12h; private custom. Auto-starts at `startsAt`; leaderboards division-scoped (#1 of D1 = King/Queen).
13. **AI verification uses the stored target.** AI challenges set `aiGenerated=true` + `metricKey`/`targetValue`; compare against the stored `targetValue` (never the client's). `active=false` disables a challenge (can't start) **without deleting** history; `safetyNotes` is shown to the player.

---

## 6. MVP core vs. supporting vs. excluded

**MVP core** — the verified-reward loop (join → AI challenge → start window → verify → **XP + seasonal points + total points** (scaled by completion) → leaderboards → two tile maps):
`User`, `Player`, `Kingdom`, `KingdomMembership`, `Challenge`, `ChallengeProgress`, `ActivityRecord`,
`PeriodScore`, `HexTile`. Demo kingdom = **Sports** (steps/distance/minutes are easy to mock-verify), built end-to-end.

**Supporting (modelled now, lighter in the demo):**
`Subscription` (premium gating; billing via webhook is roadmap), `Badge` / `PlayerBadge` (honors),
`ConnectedAccount` (OAuth auto-verify; real provider calls are roadmap), `Notification`.

**Lobbies / competitions (Premium feature set):**
`Lobby`, `LobbyMember`, `LobbyInvite`.

### Intentionally excluded for now
- **`Post` (blog)** entity — dropped from the 18-entity spec list.
- **`TileCapture` history** (conquest timeline) — only the all-time owner is kept (on `HexTile`).
- **Services, controllers, DTOs** — Phase 3, built per flow by each team member.
- **Real auth enforcement** — `SecurityConfig` is a placeholder (`permitAll`) so APIs stay testable; self-hosted JWT + role-based authorization (PLAYER/ADMIN) + a `BCrypt` password flow come in the final phase. `User.passwordHash` is already in place for it.
- **External verification adapters** (`client` / `verification` packages) — stubbed; the per-kingdom API integrations (Apple Health/Google Fit, GitHub, Moyasar, ...) are Phase 2 / Member 2.
- **AI service, realtime (Ably), Redis caching, schedulers** — infrastructure for later; not modelled as entities.
- **Separate XP-transaction ledger** — folded into `ActivityRecord` (the reward ledger: xp + seasonal + total, run-traceable via `challengeProgressId`).
- **Real Twilio/ngrok wiring** — the `LobbyInvite` model + `SMS` channel + `inviteCode` exist; the actual Twilio send + webhook controller is a Phase-3 skeleton (env-gated), not built here.

---

## 7. Shared API package & controller conventions

**Shared API classes live in `com.kingdom.API`** (team API package style):
- `com.kingdom.API.ApiResponse<T>` — generic success envelope `{ success, message, data }` with `ok(data)` / `ok(msg,data)` / `message(msg)` / `fail(msg)`. **Not** downgraded to message-only.
- `com.kingdom.API.ApiException` — base business exception (carries the HTTP status the handler returns).

**Error handling layout (unchanged locations):**
- Typed business exceptions stay in **`com.kingdom.exception`** (`ResourceNotFoundException` 404, `BadRequestException` 400, `DuplicateActionException` 409, `UnauthorizedActionException` 403, `VerificationFailedException` 422) — all extend `com.kingdom.API.ApiException`.
- Global handling stays in **`com.kingdom.advice.GlobalExceptionHandler`** → structured `ApiError` `{ timestamp, status, message, path }`.

**Controller conventions (all future controllers):**
- Return `ResponseEntity<?>` and wrap every success in `ApiResponse` — e.g. `return ResponseEntity.ok(ApiResponse.ok("Created successfully", dto));`.
- `@RestController` + `@RequestMapping("/api/...")` + `@RequiredArgsConstructor`; inject services as `private final`.
- Use `@Valid @RequestBody` **request DTOs** for input and **response DTOs** for output — **never return raw entities**.
- Use `@PathVariable` / `@RequestParam` explicitly.
- **No business logic in controllers:** no direct repository access, no XP math, no duplicate checks, no verification, no entity updates — call service methods only.
- Do not catch exceptions in controllers; let `GlobalExceptionHandler` map them.

**Package layout (with the `api` package):**
```text
com.kingdom
├── api          (ApiResponse, ApiException)   ← shared, do not modify
├── advice       (GlobalExceptionHandler)
├── config · controller · service · repository · model · enums · util · verification
├── dto/request · dto/response
└── exception    (typed business exceptions)
```
