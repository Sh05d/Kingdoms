# Kingdom — Active Master Specification

> **Status:** ACTIVE source of truth for the Kingdom backend (Phase 3 design).
> The original/older spec is archived at `docs/archive/Kingdom-Master-Spec-Original.md`.
> Companion docs: `Foundation-Summary.md`, `Flow-Contracts.md`, `API-Contracts.md`,
> `Final-Team-Split.md`, `Flow-Ownership-Matrix.md`, `Merge-Plan.md`, `Implementation-Order.md`, `Entity-Mapping.md`.

---

## 1. Overview

Kingdom is a gamified **real-world task verification** platform. Players join kingdoms, complete
**AI-generated, verified** challenges, and progress. The core rule:

> Progress must come from **verified real-world activity** — never from a self-claimed completion.

The backend is the focus. AI is the **main source of challenges**; admin is **not** the normal challenge creator.

---

## 2. Roles

- **PLAYER** — join kingdoms, join challenges/lobbies, complete verified tasks, earn XP / Seasonal Points /
  Total Points, capture tiles, appear on leaderboards, choose AI-generated challenges, receive partial rewards.
- **ADMIN** — **not** the normal challenge creator. Admin: start/manage seasonal events, review manual proof,
  approve/reject suspicious submissions, disable unsafe AI challenges, monitor review queues. (Optional emergency
  manual challenge creation exists but is **not** the product flow.)

Enum: `UserRole { PLAYER, ADMIN }`.

---

## 3. Kingdoms & per-kingdom progression

A kingdom is a category of real-world improvement. **Progress is separated per kingdom**: XP, division,
Seasonal Points, Total Points, leaderboards, and tile maps are all scoped to one kingdom. A player can be
Division 1 in Fitness and Division 3 in Reading at the same time.

Enum: `KingdomType { SPORTS, LEARNING, CHARITY, GAMING, VOLUNTEERING, FAITH, NUTRITION, READING, CREATOR, OPEN_CHALLENGE }`.
Demo kingdom = **Sports** (steps / distance / workout minutes are easy to mock-verify).

---

## 4. Free player kingdom limit

- Free players may join **2 normal kingdoms**.
- **`OPEN_CHALLENGE` does NOT count** toward the limit.
- Premium players bypass the limit.

Enforcement: count active joins **excluding** `OPEN_CHALLENGE`
(`countByPlayerIdAndActiveTrueAndKingdomIdNot(playerId, openChallengeKingdomId) < 2`); joining `OPEN_CHALLENGE`
is always allowed; the 3rd+ **normal** kingdom requires Premium.

---

## 5. OPEN_CHALLENGE kingdom

- Open-category kingdom. **No** normal daily/weekly/monthly/yearly feed.
- Challenges exist **only through lobbies**.
- Premium players create the lobbies; free players may join the kingdom (it's exempt) but still cannot create lobbies.
- Private lobbies here may use a **custom AI prompt**; public lobbies show generated options.
- Rewards follow lobby reward rules (§16): **public lobby = XP only · private lobby = no rewards**.

---

## 6. Divisions

Per kingdom: `Division { D1 = Elite, D2 = Experienced, D3 = Newbie }`, **derived from XP** (never stored):
`D1 ≥ 25,000 · D2 10,000–24,999 · D3 0–9,999`. Division matters because challenge difficulty is relative to it.

---

## 7. Difficulty (backend-internal, not a user step)

`Difficulty { EASY, MEDIUM, HARD }` still exists **internally** for AI target balancing (e.g. D3 Hard ≈ D2 Easy
in steps). **It is NOT a user-facing lobby-creation step** — the player never picks Easy/Medium/Hard. The model
keeps `Challenge.difficulty` / `Lobby.difficulty` as **internal/optional** fields the backend sets while balancing.

---

## 8. AI-generated challenges (the main source)

AI generates challenges from: kingdom · player division · duration · verification method · lobby type ·
(private only) custom prompt · internal difficulty balancing · safety rules.

Durations: `Period { DAILY, WEEKLY, MONTHLY, YEARLY }` (private lobbies may use a custom window).

A generated challenge includes: title, description, kingdom, duration, **internal** difficulty, **XP reward**,
**Seasonal Points reward**, **Total Points reward**, verification type, **metricKey**, **targetValue**, safetyNotes.

The backend stores the target. **The client never controls the verification target.**

---

## 9. Challenge verification fields (model — already applied)

```java
private String  metricKey;     // STEPS, WORKOUT_MINUTES, CALORIES, DISTANCE_KM, STUDY_MINUTES, PAGES_READ, VOLUNTEER_HOURS
private Integer targetValue;   // TRUSTED backend target (verification compares against this, never a client value)
private String  safetyNotes;   // e.g. "Stop if you feel pain", "Manual proof required"
private Boolean active = true;  // false → cannot be started; history is NOT deleted
private Integer xpReward;
private Integer seasonalPointsReward;
private Integer totalPointsReward;
```

Rules: AI metric challenges set `metricKey` + `targetValue`; verification compares the verified value to the
**stored** `targetValue`; `active=false` disables a challenge without deleting its history.

---

## 10. Challenge lifecycle

A player must **start** a challenge before tracking begins; only activity inside the window counts.

1. AI generates challenge **options**. 2. Player **chooses one** (no regenerate — §20). 3. System persists/attaches
the challenge. 4. Player **starts** it → `challengeStartAt`/`challengeEndAt` set. 5. Real-world activity occurs.
6. Player verifies. 7. Backend verifies (mock API / real API / manual). 8. Backend computes **completion %**.
9. Backend computes XP + Seasonal + Total rewards (scaled by completion). 10. Backend calls **`RewardService`**.
11. `RewardService` writes the reward ledger + membership/period score. 12. Leaderboards & tile maps update.

> **Activity before `challengeStartAt` does not count.** MVP: **one active challenge per kingdom**.

---

## 11. Verification

Core backend feature. Supports: mock fitness API verification · manual proof upload · admin manual review ·
future real API integrations · **Twilio webhook (via ngrok)** for private-lobby invites.

Fitness verification reads steps / distance / calories / workout minutes **between `challengeStartAt` and
`challengeEndAt`**. The backend computes completion; the user never submits their own completion %.

---

## 12. Partial completion rewards

`completionRate = min(100%, verifiedValue / targetValue)`. Rewards scale by it:

```text
xpAwarded            = xpReward            × completionRate
seasonalPointsAwarded = seasonalPointsReward × completionRate
totalPointsAwarded   = totalPointsReward   × completionRate
```

Rules: capped at 100%; computed from **verified** data (client never sends %); applies to **metric** challenges;
manual-proof challenges are admin-decided.

---

## 13. The three currencies

| Currency | Stored on | Used for | Resets? |
|---|---|---|---|
| **XP** | `KingdomMembership.xp` | level + **division** progression | never |
| **Seasonal Points** | `PeriodScore.seasonalPoints` | current-season tiles + season leaderboard | **each season** (period window) |
| **Total Points** | `KingdomMembership.totalPoints` | all-time tiles + all-time leaderboard | never |

XP does **not** directly control tiles. A "season" = the active `period` + `periodStart` window.

---

## 14. Tile maps (two pages)

- **Current-season map** — `currentSeasonTiles = floor(seasonalPoints / 100)` (`PeriodScore.getSeasonTiles()`); resets each season.
- **All-time map** — `allTimeTiles = floor(totalPoints / 100)` (`KingdomMembership.getTilesOwned()`); never resets.

**100 Seasonal Points = 1 current-season tile. 100 Total Points = 1 all-time tile.** (Tiles are NOT from XP.)
Each player has a unique colour (`Player.colorHex`); more points = larger contiguous territory.

---

## 15. Leaderboards

Scoped per kingdom. **Current-season leaderboard** uses Seasonal Points; **all-time leaderboard** uses Total Points.
Division is still computed from XP. Leaderboards are division-scoped; #1 of D1 each period = King/Queen.

```text
XP            → level / division
Seasonal Points → current-season map + leaderboard
Total Points  → all-time map + leaderboard
```

---

## 16. Reward impact table (LOCKED)

| Source | XP | Seasonal Points | Total Points | Tiles | Leaderboard |
|---|:--:|:--:|:--:|:--:|:--:|
| **Normal AI kingdom challenge** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Public lobby challenge** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Private lobby challenge** | ❌ | ❌ | ❌ | ❌ | ❌ |

```text
Public Lobby  = XP only
Private Lobby = no rewards
Normal AI Kingdom Challenge = XP + Seasonal Points + Total Points
```

Public lobbies let players progress **level/division (via XP)** but do **not** affect kingdom tile ownership or
seasonal/all-time competition. **Mechanism (no model change):** the reward scope is encoded in the persisted
`Challenge` at generation time — a public-lobby challenge is saved with `seasonalPointsReward = 0` and
`totalPointsReward = 0`; a private-lobby challenge awards nothing (the flow does not call `RewardService`).

---

## 17. Public lobbies

- Created by **Premium** players only; publicly browsable; others can join.
- **Same-division only:** only players whose division (in the lobby's kingdom) equals `Lobby.division` may join.
- Challenge is AI-generated from kingdom + division + duration + internal balancing; **player picks from a list of
  options — no regenerate, no AI prompt controls** (§20).
- Rewards: **XP only** (§16) — no seasonal/total points, no tiles, no public leaderboard.

---

## 18. Private lobbies

- Created by **Premium** players only; **invite-only**, not browsable.
- Host invites by phone → **Twilio SMS**; the invitee replies; the backend receives a **Twilio webhook (via ngrok
  in dev)** and matches `LobbyInvite.inviteCode`. Invite statuses: `PENDING, ACCEPTED, REJECTED, EXPIRED`.
- May use a **custom AI prompt** (e.g. "a 150,000-step challenge for 2 weeks") → AI generates the challenge.
- Rewards: **none** (no XP, no points, no tiles, no leaderboard). Custom prompts are allowed precisely because they
  give no rewards — this prevents abuse.

---

## 19. Simplified lobby creation flow (no difficulty page)

1. Premium player chooses **lobby type** (public / private).
2. Player chooses **kingdom**.
3. System reads the creator's **division** in that kingdom (locks `Lobby.division` for public lobbies).
4. System **auto-generates** suitable challenge options (kingdom + division + lobby type + duration/rules +
   **internal** difficulty balancing). **No user-facing difficulty step.**
5. Player **selects one** challenge from the generated list (public) — or, for private, may write a **custom prompt**.
6. Player chooses **start time** (public ≤ 12h duration; private custom).
7. Lobby is created (auto-starts at `startsAt`; cannot be started manually; cannot cancel if `< 8h` to start).

---

## 20. Challenge selection (options only, no regenerate)

The selection page shows **only a list of generated options**:

```text
Choose a challenge:
1. Walk 7,000 steps today
2. Complete 30 workout minutes
3. Burn 300 active calories
```

The player **selects one**. There is **no** "Generate new", "Regenerate", or "Ask AI again" control in the normal
(personal + public-lobby) flow — no AI prompt controls are shown to normal users. **Exception:** private lobbies
(invite-only, no rewards) may use a host custom prompt.

---

## 21. Premium rules

- **Premium:** create public lobbies · create private lobbies · custom AI prompt (private) · join more than 2 normal kingdoms.
- **Free:** join up to 2 normal kingdoms · join `OPEN_CHALLENGE` (exempt) · join lobbies if allowed · complete challenges · earn XP/points on allowed challenges.
- **Free cannot:** create lobbies · create custom private-lobby challenges.

Premium is read from `Subscription` (status `ACTIVE`, plan `PREMIUM`); no subscription ⇒ FREE.

---

## 22. Admin responsibilities & seasonal events

Admin: start/manage **seasonal events**, review manual proof, approve/reject suspicious submissions, disable unsafe
AI challenges (`active=false`), view review queues. Seasonal events **reuse the lobby/event system**
(`LobbyKind.SEASONAL`, `createdByAdmin=true`); fields: title, description, kingdom, start/end, theme, active.

---

## 23. Twilio & ngrok (private invites)

Host creates a private lobby → enters invitee phone → backend sends **Twilio SMS** (invite code) → invitee replies →
**Twilio webhook** hits the backend (**ngrok** exposes it in dev) → backend validates phone/`inviteCode` → adds the
player. `LobbyInvite { lobbyId, phone, inviteCode, invitedPlayerId, channel(SMS), status, sentAt, respondedAt }`.

---

## 24. Backend stack & architecture

Java 17 · Spring Boot 4.0.6 · Maven · MySQL · Spring Web · Spring Data JPA · Validation · Lombok · REST.
Security deferred in dev (permit-all); final = register/login, BCrypt, JWT, PLAYER/ADMIN roles, premium checks.
Twilio for SMS; ngrok for local webhook testing.

Layered: `Controller → Service → Repository → MySQL`. Controllers handle HTTP only, return `ResponseEntity<?>`,
wrap success in `ApiResponse`, hold no business logic, never expose entities. Shared API in **`com.kingdom.API`**
(`ApiResponse`, `ApiException`); business exceptions in `com.kingdom.exception`; global handling in
`com.kingdom.advice.GlobalExceptionHandler`.

---

## 25. Backend data separation

- **`KingdomMembership`** — long-term per-kingdom progress: `xp` (level/division) + `totalPoints` (all-time) + `active`. Derives division + all-time tiles.
- **`PeriodScore`** — current-season competition: `seasonalPoints` per period window. Derives season tiles.
- **`ActivityRecord`** — the **reward ledger / audit**: `membershipId`, `challengeProgressId` (UNIQUE → no duplicate award), `xpAwarded`, `seasonalPointsAwarded`, `totalPointsAwarded`, `source`, `externalId` (UNIQUE with source).

---

## 26. Critical ownership rule

**Only `RewardService` may** write XP, write Seasonal Points, write Total Points, create challenge-based
`ActivityRecord` rows, update `KingdomMembership` (`xp`/`totalPoints`), and update `PeriodScore` (`seasonalPoints`).

Verification / admin-review services **verify activity, compute completion %, and request the award** — they must
**never** write XP/points directly. Signature:
`RewardService.awardReward(membershipId, challengeProgressId, xpAwarded, seasonalPointsAwarded, totalPointsAwarded, source, externalId, challengeId)`.

---

## 27. Team split (Phase 3)

- **Member 1 — User + Kingdom + Lobby foundation:** players/admin, profile, kingdoms, join (+ free-2 limit & OPEN_CHALLENGE exemption), lobby create/join (public same-division), private invite structure, start time.
- **Member 2 — AI Challenge Generation + Submission + Verification (hardest):** AI generation (personal + lobby + private custom prompt), challenge selection/start, submission, mock fitness verification, manual proof, attempt logging, **partial completion**, calls `RewardService` — never writes rewards.
- **Member 3 — Rewards + Leaderboard + Admin Events/Review:** `RewardService` (XP + seasonal + total), duplicate-reward prevention, `ActivityRecord`, membership/period updates, division & tile calculation, season + all-time leaderboards, admin events, manual review, notifications, demo data.

Full ownership: `Final-Team-Split.md` + `Flow-Ownership-Matrix.md`.

---

## 28. MVP backend priorities

User/membership → free/premium limits → OPEN_CHALLENGE exemption → AI generation (mock/rule engine) → lobby
creation (no difficulty page) → public same-division join → private Twilio invite skeleton → challenge start +
tracking window → verification → partial reward calc → **XP/Seasonal/Total** awarding → tile calculation →
leaderboards → admin review/events → final security/JWT. Strongest demo = **Sports**.

---

## 29. Most important business rules

1. XP → level/division. 2. Seasonal Points → current-season tiles/leaderboard. 3. Total Points → all-time
tiles/leaderboard. 4. Seasonal Points reset each season; XP and Total Points never reset. 5. 100 Seasonal Points =
1 season tile; 100 Total Points = 1 all-time tile; **XP does not control tiles**. 6. No XP/points without
verification. 7. Activity counts only after start, only inside the window. 8. Partial reward = base × verified
completion. 9. **Public lobby = XP only; private lobby = no rewards; normal AI challenge = XP + Seasonal + Total.**
10. Public lobbies require **same-division** players. 11. Private lobbies are invite-only via **Twilio**. 12. Free
players: 2 normal kingdoms; `OPEN_CHALLENGE` is exempt. 13. Only Premium creates lobbies. 14. **Lobby creation has
no user-facing difficulty step.** 15. **Challenge selection shows options only — no regenerate** (private custom
prompt excepted). 16. AI is the main challenge source; admin is not the normal creator. 17. Only `RewardService`
writes XP/points. 18. Verification uses **server-side** target values. 19. Disabled (`active=false`) challenges
can't be started; history is kept. 20. Security deferred in dev, required before final submission.

---

## 30. Naming corrections (vs the archived original)

- `KingdomType.SPECIAL_EVENTS` → **`OPEN_CHALLENGE`**.
- Reward service `XpService` → **`RewardService`** (writes all 3 currencies).
- `PeriodScore.xp` → **`seasonalPoints`**; added `KingdomMembership.totalPoints`.
- Private-lobby invite channel WhatsApp → **Twilio SMS** (`InviteChannel.SMS`; `InviteStatus.EXPIRED` added).
- One XP currency → **three currencies** (XP / Seasonal / Total). Tiles from points, **not** XP.
