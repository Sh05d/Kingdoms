# Anas — Flow Endpoints (the demo path, 26)

My endpoints in the order the demo uses them. `↔` marks where a teammate's endpoint plugs in (so you can
wire the integrated flow). Dev/debug endpoints (`/verify/charity/check`, `/verify/fitness/check`,
`/verify/test-welcome-email`) are left out — they're not part of the flow.

Base: `/api/v1`

---

### 1 · Phone verification (2)
| # | Method | Path | What |
|---|---|---|---|
| 1 | POST | `/auth/send-otp?phone` | Send a 6-digit OTP over WhatsApp |
| 2 | POST | `/auth/verify-otp?phone&code` | Verify the OTP → send the welcome email |

> ↔ before this: **register** (`POST /user/add`, Maysun) creates the user/player.

### 2 · Connect verification accounts (3)
| # | Method | Path | What |
|---|---|---|---|
| 3 | GET | `/verify/fitness/connect/{playerId}` | Strava OAuth — returns the authorize URL to open |
| 4 | GET | `/verify/fitness/callback?code&state` | Strava redirects here — stores the player's refresh token |
| 5 | POST | `/connecte/link/{playerId}` | Link any provider (e.g. **Steam** for Gaming, body `{provider, externalUserId}`) |

> ↔ before this: **join a kingdom** (`POST /kingdom-membership/join/{playerId}/{kingdomId}`, Maysun); 3rd+
> kingdom / lobby needs **premium** (`POST /subscription/add/{playerId}`, Maysun).

### 3 · Challenge generation & browse (4)
| # | Method | Path | What |
|---|---|---|---|
| 6 | POST | `/challenge/generate?kingdomId&difficulty&period` | **AI-generate** a challenge (Fitness/Charity/Volunteer; Arabic) |
| 7 | GET | `/challenge/kingdom/{kingdomId}` | List active challenges in a kingdom |
| 8 | GET | `/challenge/difficulty/{difficulty}` | Browse by difficulty |
| 9 | GET | `/challenge/period/{period}` | Browse by period |

> ↔ Reading/Gaming/Faith use Shahad's generate (`POST /challenge/kingdom/{kingdomId}/generate`) instead of #6.

### 4 · Play loop — join, finish, track (7)
| # | Method | Path | What |
|---|---|---|---|
| 10 | POST | `/challenge-progress/join/{playerId}/{challengeId}` | **Join (start)** a challenge |
| 11 | POST | `/challenge-progress/finish/{id}` | **Finish + verify** → XP + daily streak + division on pass |
| 12 | POST | `/challenge-progress/cancel/{id}` | Cancel an active run |
| 13 | GET | `/challenge-progress/player/{playerId}` | A player's runs |
| 14 | GET | `/challenge-progress/player/{playerId}/active` | A player's active runs |
| 15 | GET | `/challenge-progress/player/{playerId}/status/{status}` | A player's runs by status |
| 16 | GET | `/challenge-progress/streak/{playerId}/{kingdomId}` | Daily streak in a kingdom |

### 5 · Verification step (depends on the kingdom, runs around #11) (7)
| # | Method | Path | What |
|---|---|---|---|
| 17 | POST | `/verify/charity/link?psuId` | Charity: Neotek Open-Banking account link (consent) |
| 18 | GET | `/verify/charity/accounts?psuId` | Charity: list linked bank accounts |
| 19 | GET | `/verify/charity/transactions?psuId` | Charity: list bank transactions |
| 20 | POST | `/verify/charity/donate?psuId&charityName&amountSar` | Charity: DEMO simulated donation (so finish passes) |
| 21 | GET | `/verify/fitness/activities?fromDays` | Fitness: list the athlete's Strava activities |
| 22 | POST | `/verify/volunteer/upload` *(multipart `file`)* | Volunteer: upload a certificate PDF — AI-verified |
| 23 | POST | `/verify/volunteer/whatsapp` | WhatsApp inbound webhook (router): PDF → volunteer, A/B/C/D text → quiz |

### 6 · Account views (2)
| # | Method | Path | What |
|---|---|---|---|
| 24 | GET | `/connecte/player/{playerId}` | A player's linked accounts |
| 25 | DELETE | `/connecte/player/{playerId}/{provider}` | Disconnect a provider |

### 7 · Lobby winner (1)
| # | Method | Path | What |
|---|---|---|---|
| 26 | POST | `/lobby-challenge/resolve/{lobbyId}` | **Resolve the winner** — first member to finish+verify the lobby's challenge; hype WhatsApp, no XP, removes the lobby |

> ↔ before #26: the lobby itself is Maysun's — `POST /lobby/create/{kingdomId}/{challengeId}/{hostPlayerId}`,
> `POST /lobby-member/join/{lobbyId}/{playerId}`. The member finishes via #11, then #26 picks the winner.
