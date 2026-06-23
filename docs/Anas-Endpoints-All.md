# Anas — API Endpoints (everything, including CRUD)

My part of the Kingdom backend: the **Challenge play loop** (Fitness / Charity / Volunteer kingdoms),
**external-account links + verification** (Strava / Neotek Open Banking / AI-PDF), **phone verification (OTP) +
welcome email**, and the **lobby winner**. All paths are prefixed with the base shown per controller.

> Excludes teammate work: Maysun (User / Player / Kingdom / KingdomMembership / Subscription / Lobby / Badge)
> and Shahad (Reading / Gaming / Faith AI, the quiz, kingdom recommendation, player report). Two endpoints on
> `ChallengeController` (`POST /add/{kingdomId}` and `POST /kingdom/{kingdomId}/generate`) are Shahad's and are
> NOT listed here.

**44 endpoints across 6 controllers** — Challenge 9 · ChallengeProgress 12 · ConnectedAccount 8 · Verification 12 · Auth 2 · LobbyChallenge 1.

---

## Challenge — `/api/v1/challenge`
Create, browse, and AI-generate challenges.

| Method | Path | Description |
|---|---|---|
| GET | `/get` | List all challenges |
| POST | `/add` | Create a challenge (manual, validated DTO) |
| POST | `/generate?kingdomId&difficulty&period` | **AI-generate** a verifiable challenge for a kingdom (Arabic title/description; fixed XP) |
| PUT | `/update/{id}` | Update a challenge |
| DELETE | `/delete/{id}` | Delete a challenge |
| GET | `/get/{id}` | Get a challenge by id |
| GET | `/kingdom/{kingdomId}` | List active challenges in a kingdom |
| GET | `/difficulty/{difficulty}` | Browse challenges by difficulty |
| GET | `/period/{period}` | Browse challenges by period |

## Challenge Progress — `/api/v1/challenge-progress`
The play loop: join → finish (verify) → XP / streak / division.

| Method | Path | Description |
|---|---|---|
| GET | `/get` | List all progress records |
| POST | `/add` | Create a progress record (admin/testing) |
| PUT | `/update/{id}` | Update a progress record |
| DELETE | `/delete/{id}` | Delete a progress record |
| GET | `/get/{id}` | Get a progress record by id |
| POST | `/join/{playerId}/{challengeId}` | **Join (start)** a challenge |
| POST | `/finish/{id}` | **Finish** a run — runs the kingdom's verification; awards XP + daily streak + division on pass |
| POST | `/cancel/{id}` | Cancel an active run |
| GET | `/player/{playerId}` | All of a player's runs |
| GET | `/player/{playerId}/active` | A player's active (unfinished) runs |
| GET | `/player/{playerId}/status/{status}` | A player's runs filtered by status |
| GET | `/streak/{playerId}/{kingdomId}` | A player's daily streak in a kingdom |

## Connected Account — `/api/v1/connecte`
Link external providers (Strava / Steam / Neotek) used for verification.

| Method | Path | Description |
|---|---|---|
| GET | `/get` | List all connected accounts |
| POST | `/add` | Create a connected account |
| PUT | `/update/{id}` | Update a connected account |
| DELETE | `/delete/{id}` | Delete a connected account |
| GET | `/get/{id}` | Get a connected account by id |
| POST | `/link/{playerId}` | **Link** an external provider account for a player |
| GET | `/player/{playerId}` | A player's linked accounts |
| DELETE | `/player/{playerId}/{provider}` | Disconnect a provider for a player |

## Verification — `/api/v1/verify`
The per-kingdom verification engines + WhatsApp inbound + email.

| Method | Path | Description |
|---|---|---|
| POST | `/charity/link?psuId` | Create a Neotek Open-Banking account link (consent) |
| GET | `/charity/accounts?psuId` | List the linked bank accounts |
| GET | `/charity/transactions?psuId` | List bank transactions |
| POST | `/charity/donate?psuId&charityName&amountSar` | **DEMO**: record a simulated donation so a charity finish can pass |
| GET | `/charity/check?psuId&minAmountSar&...` | Check whether a qualifying donation exists |
| GET | `/fitness/connect/{playerId}` | **Per-player Strava OAuth** — returns the authorize URL to connect |
| GET | `/fitness/callback?code&state` | Strava OAuth callback — stores the player's refresh token |
| GET | `/fitness/activities?fromDays` | List the athlete's Strava activities |
| GET | `/fitness/check?metricKey&targetValue&...` | Check whether a fitness target is met |
| POST | `/volunteer/upload` *(multipart `file`)* | Upload a certificate PDF — **AI-verifies** it (completes the run if `progressId` given) |
| POST | `/volunteer/whatsapp` | **Twilio WhatsApp inbound webhook (router)**: PDF → volunteer cert, text → reading/faith quiz |
| GET | `/test-welcome-email?to&name` | DEV: fire the Arabic welcome email (Mailtrap) |

## Auth (phone verification) — `/api/v1/auth`
Interim OTP verify + welcome email. *(Final auth = default Spring Security, added after the Maysun merge.)*

| Method | Path | Description |
|---|---|---|
| POST | `/send-otp?phone` | Send a 6-digit OTP over WhatsApp |
| POST | `/verify-otp?phone&code` | Verify the OTP; on success send the welcome email |

## Lobby Winner — `/api/v1/lobby-challenge`
The lobby itself is Maysun's; **the winner decision is mine.**

| Method | Path | Description |
|---|---|---|
| POST | `/resolve/{lobbyId}` | **Resolve the winner** — first lobby member to finish+verify the lobby's challenge; sends a hype WhatsApp (no XP); removes the lobby |
