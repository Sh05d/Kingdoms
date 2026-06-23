# Anas — Endpoints (Player Challenge + Verification flows)

> **Counter: 41 endpoints** across **5 controllers**, all built **without the teammates**.
> Owned models: **Challenge, ChallengeProgress, ConnectedAccount** + the 3 kingdoms (Fitness/Charity/Volunteer).
> Excludes the team's controllers (User, Player, Kingdom, KingdomMembership, Lobby*, Badge, Subscription, …)
> **and** Shahad's one endpoint in ChallengeController (`POST /challenge/kingdom/{id}/generate`).
> Breakdown: **26 flow-specific + 15 standard CRUD**.
>
> | Controller | Base | Ours |
> |---|---|---|
> | ChallengeController | `/api/v1/challenge` | 9 (+1 Shahad, not counted) |
> | ChallengeProgressController | `/api/v1/challenge-progress` | 12 |
> | ConnectedAccountController | `/api/v1/connecte` | 8 |
> | VerificationController | `/api/v1/verify` | 11 |
> | LobbyChallengeController | `/api/v1/lobby-challenge` | 1 |
> | **Total** | | **41** |

## 1. Challenge — `/api/v1/challenge` (9)
| Method · Path | Purpose |
|---|---|
| `GET /get` | List all challenges. |
| `GET /get/{id}` | One challenge by id. |
| `POST /add` | Create a challenge (`ChallengeIn`); XP is set from the fixed `ChallengeXp` table, not the client. |
| `PUT /update/{id}` | Update a challenge. |
| `DELETE /delete/{id}` | Delete a challenge. |
| `POST /generate?kingdomId&difficulty&period` | **AI-generate** a challenge for one of Anas's kingdoms (regex parse → fixed XP). |
| `GET /kingdom/{kingdomId}` | Active challenges in a kingdom. |
| `GET /difficulty/{difficulty}` | Active challenges by difficulty (EASY/MEDIUM/HARD). |
| `GET /period/{period}` | Active challenges by period (DAILY/WEEKLY/MONTHLY/YEARLY). |

*Not ours: `POST /kingdom/{kingdomId}/generate` — Shahad's JSON generator (questions/targetName) for Reading/Gaming.*

## 2. ChallengeProgress — `/api/v1/challenge-progress` (12)
| Method · Path | Purpose |
|---|---|
| `GET /get` | List all runs. |
| `GET /get/{id}` | One run by id. |
| `POST /add` | Create a run directly (admin/testing; `ChallengeProgressIn`). |
| `PUT /update/{id}` | Update a run. |
| `DELETE /delete/{id}` | Delete a run. |
| `POST /join/{playerId}/{challengeId}` | Join = start a challenge (sets `startAt` → JOINED). A player can join many. |
| `POST /finish/{id}` | Finish: runs the kingdom's real verification; awards XP + division + streak only on pass. |
| `POST /cancel/{id}` | Cancel (leave) an active run. |
| `GET /player/{playerId}` | A player's full challenge history (all kingdoms). |
| `GET /player/{playerId}/active` | A player's currently active runs (JOINED/IN_PROGRESS). |
| `GET /player/{playerId}/status/{status}` | A player's runs filtered by one status. |
| `GET /streak/{playerId}/{kingdomId}` | A player's current daily streak in a kingdom. |

## 3. ConnectedAccount — `/api/v1/connecte` (8)
| Method · Path | Purpose |
|---|---|
| `GET /get` | List all linked accounts. |
| `GET /get/{id}` | One linked account by id. |
| `POST /add` | Create a linked account (generic; no player attached). |
| `PUT /update/{id}` | Update a linked account. |
| `DELETE /delete/{id}` | Delete a linked account. |
| `POST /link/{playerId}` | Link (or re-link) a provider for a player; upsert on (player, provider). |
| `GET /player/{playerId}` | List a player's linked accounts (token-free DTO). |
| `DELETE /player/{playerId}/{provider}` | Unlink one provider for a player. |

## 4. Verification — `/api/v1/verify` (11)
| Method · Path | Kingdom | Purpose |
|---|---|---|
| `POST /charity/link?psuId&financialInstitutionId` | Charity | Start Neotek bank consent — returns a `RedirectionURL` to approve at the bank. |
| `GET /charity/accounts?psuId` | Charity | List the PSU's bank accounts (Neotek). |
| `GET /charity/transactions?psuId` | Charity | List the PSU's bank transactions (Neotek). |
| `POST /charity/donate?psuId&charityName&amountSar` | Charity | **Demo helper** — record a simulated donation so a charity finish passes. |
| `GET /charity/check?psuId&charityName&minAmountSar&fromDays` | Charity | Did a qualifying donation happen in the window? |
| `GET /fitness/connect/{playerId}` | Fitness | **Per-player Strava OAuth** — returns the authorize URL for the player to approve. |
| `GET /fitness/callback?code&state` | Fitness | Strava OAuth redirect — stores the player's refresh token on their ConnectedAccount. |
| `GET /fitness/activities?fromDays` | Fitness | Read Strava activities (the configured/demo athlete). |
| `GET /fitness/check?metricKey&targetValue&sportType&fromDays` | Fitness | Did a Strava metric reach a target? |
| `POST /volunteer/upload` (multipart) | Volunteer | Upload a certificate PDF (by `progressId`, or `from=`phone) → AI verify → complete the run. |
| `POST /volunteer/whatsapp` (Twilio webhook) | Volunteer | Inbound WhatsApp PDF → match sender by phone → verify → complete run + hype reply. |

## 5. LobbyChallenge — `/api/v1/lobby-challenge` (1) *(optional)*
| Method · Path | Purpose |
|---|---|
| `POST /resolve/{lobbyId}` | First member to finish+verify the lobby's challenge wins (no XP); the lobby is then removed. |

> `LobbyChallengeService`/Controller only **read** the teammate `Lobby` + members and **delete** a finished lobby.

## Flow notes
- **Lifecycle:** join → finish (or cancel). No separate "start" (join *is* the start); no manual approve.
- **Multiple active challenges allowed** — a player can join many at once.
- **Finish is gated on real verification** (by the challenge's `verificationSource`): STRAVA → Strava activities;
  NEOTEK_OPEN_BANKING → bank donation; AI_PDF_MATCH → certificate PDF (WhatsApp/upload); else → pass.
  On fail the run stays JOINED (`NOT_COMPLETED`) so the player can finish again.
- **XP / division / streak** are written to the player's `KingdomMembership` (`totalXP`, `division`, `strick`).
- **Hype WhatsApp** is sent on every verified finish (best-effort; never blocks the finish).
- `ProgressStatus`: `JOINED, IN_PROGRESS, SUBMITTED, VERIFIED, REJECTED, CANCELED`.

## DTOs (no entities exposed)
- **`ChallengeOut` / `ChallengeIn`** — challenge response/request (`ChallengeIn` has no `xpReward`; the service sets it).
- **`ChallengeProgressOut` / `ChallengeProgressIn`** — run response/request (Out flattens challenge/player/kingdom ids).
- **`ConnectedAccountOut` / `ConnectedAccountIn`** — token-free response / link request.

## Recent additions (this round)
- `POST /challenge/generate` — AI challenge generation (Anas's 3 kingdoms, fixed XP).
- Verification (`/api/v1/verify/*`) — charity (Neotek), fitness (Strava) incl. **per-player OAuth connect/callback**,
  and volunteer (AI PDF) over **upload + WhatsApp**, with phone-based run completion.

_Last updated: 2026-06-19._
