# Anas — Main Endpoints (the real features, excluding generic CRUD)

Only my **non-CRUD** endpoints — the challenge play loop, the verification engines, OTP, and the lobby winner.
The plain `GET /get` · `POST /add` · `PUT /update/{id}` · `DELETE /delete/{id}` · `GET /get/{id}` on each
controller are omitted here (see `Anas-Endpoints-All.md` for those).

> My part only — excludes Maysun (User/Player/Kingdom/Subscription/Lobby/Badge) and Shahad (Reading/Gaming/Faith
> AI, quiz, recommendation, player report).

**29 endpoints.**

---

## Challenge generation & browsing — `/api/v1/challenge`
| Method | Path | Description |
|---|---|---|
| POST | `/generate?kingdomId&difficulty&period` | **AI-generate** a verifiable challenge (Arabic title/description, fixed XP) |
| GET | `/kingdom/{kingdomId}` | List active challenges in a kingdom |
| GET | `/difficulty/{difficulty}` | Browse by difficulty |
| GET | `/period/{period}` | Browse by period |

## Challenge play loop — `/api/v1/challenge-progress`
| Method | Path | Description |
|---|---|---|
| POST | `/join/{playerId}/{challengeId}` | **Join (start)** a challenge |
| POST | `/finish/{id}` | **Finish + verify** — awards XP + daily streak + division on pass |
| POST | `/cancel/{id}` | Cancel an active run |
| GET | `/player/{playerId}` | A player's runs |
| GET | `/player/{playerId}/active` | A player's active runs |
| GET | `/player/{playerId}/status/{status}` | A player's runs by status |
| GET | `/streak/{playerId}/{kingdomId}` | A player's daily streak in a kingdom |

## External account linking — `/api/v1/connecte`
| Method | Path | Description |
|---|---|---|
| POST | `/link/{playerId}` | Link an external provider account (Strava / Steam / Neotek) |
| GET | `/player/{playerId}` | A player's linked accounts |
| DELETE | `/player/{playerId}/{provider}` | Disconnect a provider |

## Verification engines — `/api/v1/verify`
| Method | Path | Description |
|---|---|---|
| POST | `/charity/link?psuId` | Create a Neotek Open-Banking account link (consent) |
| GET | `/charity/accounts?psuId` | List linked bank accounts |
| GET | `/charity/transactions?psuId` | List bank transactions |
| POST | `/charity/donate?psuId&charityName&amountSar` | DEMO: record a simulated donation (so a charity finish passes) |
| GET | `/charity/check?...` | Check for a qualifying donation |
| GET | `/fitness/connect/{playerId}` | Per-player Strava OAuth — get the authorize URL |
| GET | `/fitness/callback?code&state` | Strava OAuth callback — stores the refresh token |
| GET | `/fitness/activities?fromDays` | List Strava activities |
| GET | `/fitness/check?metricKey&targetValue&...` | Check a fitness target |
| POST | `/volunteer/upload` *(multipart `file`)* | Upload a certificate PDF — AI-verified |
| POST | `/volunteer/whatsapp` | Twilio WhatsApp inbound webhook (router): PDF → volunteer, text → quiz |
| GET | `/test-welcome-email?to&name` | DEV: fire the Arabic welcome email |

## Phone verification (OTP) — `/api/v1/auth`
| Method | Path | Description |
|---|---|---|
| POST | `/send-otp?phone` | Send a 6-digit OTP over WhatsApp |
| POST | `/verify-otp?phone&code` | Verify the OTP → send the welcome email |

## Lobby winner — `/api/v1/lobby-challenge`
| Method | Path | Description |
|---|---|---|
| POST | `/resolve/{lobbyId}` | Resolve the lobby winner (first to finish+verify); hype WhatsApp, no XP, removes the lobby |

---

## What's behind these (the features I built)
- **Per-kingdom AI challenge generation** — each kingdom (Fitness/Charity/Volunteer) has its own AI service that
  produces a challenge the verifier can actually check; output is validated (source + positive target) so nothing
  un-verifiable is saved. Title/description in **Arabic**.
- **Fitness → Strava (per-player OAuth2)** — each player connects their own Strava; finish reads their recorded
  activities (distance / moving-time / activity-count) within the challenge's period.
- **Charity → Neotek Open Banking** — reads booked bank transactions to confirm a donation (Saudi sandbox);
  includes a donate simulator for the demo.
- **Volunteer → AI PDF check** — the player sends a certificate PDF (WhatsApp or upload); OpenAI reads it and
  approves/rejects against the challenge.
- **WhatsApp (Twilio)** — outbound **hype** on join / finish / lobby win (Arabic), the **OTP** for phone
  verification, and a single **inbound webhook router** (PDF → volunteer, A/B/C/D text → quiz).
- **Welcome email** — Mailtrap HTTP-API client, Arabic + RTL, sent on a verified phone.
- **XP / streak / division** — one shared `ProgressRewardService` applies the fixed-XP table, daily per-kingdom
  streak, and division on every verified finish.
- **Lobby winner** — among a lobby's members, the first to finish+verify the lobby's challenge wins (no XP);
  the lobby is then removed.
