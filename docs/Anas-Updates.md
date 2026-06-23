# Anas — Spec Updates (New Rules)

> Short addendum to the master spec. These are the **new rules** added on Anas's branch (`anas-ai`).
> They only touch Anas's own models plus writes to the teammate `KingdomMembership` through its existing setters.
> Owned models: **Challenge, ChallengeProgress, ConnectedAccount**.

---

## 1. Join multiple challenges at once

- A player can now be **in many challenges at the same time**.
- The old "only one active challenge" limit is **removed**.
- Joining a challenge is still the same as starting it (no separate start step).

## 2. XP on finish

- When a player **finishes** a challenge, that challenge's `xpReward` is **added** to their
  `KingdomMembership.totalXP`.
- XP is tracked **per kingdom** (each membership has its own `totalXP`).

## 3. Divisions

- There are **3 divisions**. A **lower number means a higher division** — **D1 is the top**.
  - `D3` = 0 – 9,999 XP
  - `D2` = 10,000 – 24,999 XP
  - `D1` = 25,000 XP and above
- (This matches the range documented on `KingdomMembership`.)
- The division is **recomputed every time a challenge is finished** (from the new total XP) and saved on
  `KingdomMembership.division`.

## 4. Daily kingdom streak

- The streak counts days in a row that you finished a challenge **in that kingdom**. It is **per kingdom**
  (stored on `KingdomMembership.strick`).
- When you finish a challenge:
  - Finished one in this kingdom **yesterday** → streak **+1**.
  - Already finished one in this kingdom **today** → streak **unchanged**.
  - First ever, or you **missed a day** → streak **resets to 1**.
- **New endpoint:** `GET /api/v1/challenge-progress/streak/{playerId}/{kingdomId}` — returns the player's
  current streak number in that kingdom.

## 5. New Challenge fields

- `Challenge` now has two **nullable** fields:
  - `metricKey` (String) — **what** verification measures, e.g. `"steps"`.
  - `targetValue` (Integer) — **the number** the player must reach, e.g. `8000`.
- They are nullable on purpose so older challenges that don't set them still work (no teammate code breaks).

## 6. AI difficulty matches the player's division

- When AI generates a challenge for a player, the **difficulty scales to their division**:
  - Division `1` → **HARD**
  - Division `2` → **MEDIUM**
  - Division `3` → **EASY**
- The AI **no longer decides** difficulty, period or XP. The app chooses `difficulty` (from the division
  above) and `period`, and passes them **into** the AI. The new method is
  `generateChallenge(Difficulty, Period, List<String> existingChallenges)` — the list lets the AI avoid
  repeating challenges that already exist.
- All 9 kingdom AIs now share **one base prompt** (`ChallengePrompts.build(...)`); only each kingdom's
  name, how it's verified, and its allowed `metricKey` differ.

## 6b. XP is fixed per challenge (the AI does NOT invent it)

- The AI used to make up the XP, which gave inconsistent, unfair numbers. XP is now a **fixed table**
  based on `(difficulty, period)` — see `ChallengeXp.xpFor(...)`. The same difficulty + period always
  award the same XP.

  |          | EASY | MEDIUM | HARD |
  |----------|------|--------|------|
  | DAILY    | 50   | 80     | 120  |
  | WEEKLY   | 100  | 140    | 180  |
  | MONTHLY  | 200  | 250    | 300  |
  | YEARLY   | 400  | 500    | 600  |

- `YEARLY` wasn't in the first draft — extrapolated so `Period.YEARLY` doesn't fall through; tweak freely.
- When a challenge is created from the AI output, set its reward with
  `challenge.setXpReward(ChallengeXp.xpFor(difficulty, period))`.

## 7. Anas's 3 kingdoms + how they verify

| Kingdom | Theme | How a finish is verified |
|---|---|---|
| Fitness | Sport / activity | **Google Health** data (steps, distance, etc.) |
| Charity | Donations | **Neotek Open Banking** (bank transaction check) |
| Volunteer | Volunteering | **AI PDF matching** (read the proof PDF and match it to the challenge) |

## 8. Lobby challenge

A lobby is a small race: the **first member to finish AND verify the lobby's challenge wins**, **no XP** is
awarded, and the **lobby is removed** afterwards.

**BUILT (isolated, easy to switch off):**
- `LobbyChallengeService` + `LobbyChallengeController` — find the winner + remove the lobby.
- **Endpoint:** `POST /api/v1/lobby-challenge/resolve/{lobbyId}` → returns the winner (or "no winner yet").
- They only **read** the teammate `Lobby` + its members and **delete** a finished lobby — **no `Lobby` model change**.
- To turn the feature off, just comment out (slash out) those two classes — nothing else depends on them.

**Still owned by the Lobby teammate (not built here):**
- **PUBLIC** lobbies — players must be the **same division** to join (lives in their `LobbyService` join logic).
- **PRIVATE** lobbies — division ignored, no XP (just for fun).
