# Flow Ownership Matrix — Kingdom backend (Phase 3, AI-first)

The who-owns-what grid for safe parallel work. Authoritative split: `Final-Team-Split.md`.
Flow detail: `Flow-Contracts.md`. Endpoints: `API-Contracts.md`.

Legend: **W** = writes (single owner) · **R** = reads · — = not used.

---

## 1. Entity ownership

| Entity | M1 (User/Kingdom/Lobby) | M2 (AI/Submission/Verify) | M3 (XP/Leaderboard/Admin) | Notes |
|---|:--:|:--:|:--:|---|
| `User` | **W** | — | R | account + role |
| `Player` | **W** | R | R | game profile |
| `Subscription` | **W/R** | — | — | premium gate read; mgmt deferred |
| `Kingdom` | **W** (seed) | R | R | 10 seeded by M1 |
| `KingdomMembership` | **W** (create) | R | **W** (`xp`/`level`) | shared-write — field split |
| `Lobby` | **W** | R + attach via M1 | R + seasonal via M1 | single writer M1 |
| `LobbyMember` | **W** | R | R | |
| `LobbyInvite` | **W** (opt.) | — | — | private invites |
| `Challenge` | R | **W** (AI main) | R | fields incl. `metricKey`/`targetValue`/`safetyNotes`/`active`; admin emergency + disable via M2's `ChallengeService` |
| `ChallengeProgress` | — | **W** | R + mutate via M2 | admin review delegates to M2 |
| `ActivityRecord` | — | R | **W** (`RewardService` only) | XP ledger; `challengeProgressId` |
| `PeriodScore` | — | — | **W** | leaderboard buckets |
| `Notification` | — | — | **W** | optional |
| `HexTile`,`Badge`,`PlayerBadge` | — | — | — | deferred |

**Shared-write protocol:** `KingdomMembership` (M1 creates identity fields; M3 mutates `xp`/`level`) ·
`ChallengeProgress` (M2 sole writer; M3 approves/rejects through `SubmissionService.markApproved/markRejected`) ·
`Lobby` (M1 sole writer; M2 attaches a challenge through `LobbyService.attachChallenge`; M3 creates seasonal lobbies through `LobbyService.createSeasonalLobby`).

---

## 2. Service ownership

| Service | Owner | Writes | Key callers |
|---|:--:|---|---|
| `UserService`, `PlayerService` | M1 | User, Player | — |
| `MembershipService` | M1 | KingdomMembership (create) | — |
| `LobbyService` | M1 | Lobby, LobbyMember | M2 (`attachChallenge`), M3 (`createSeasonalLobby`) |
| `AiChallengeService` | M2 | Challenge (AI) | — (entry via controllers) |
| `ChallengeService` | M2 | Challenge (incl. `active` toggle) | M3 (`createChallenge` for events; `setActive` for disable) |
| `SubmissionService` | M2 | ChallengeProgress | M3 (`markApproved`/`markRejected`) |
| `VerificationService` | M2 | ChallengeProgress (status) | — → calls `RewardService` |
| `AiChallengeGenerator` / `MockAiChallengeGenerator` | M2 | — (pure) | `AiChallengeService` |
| `RewardService` | M3 | ActivityRecord (xp/seasonal/total), KingdomMembership.xp+totalPoints, PeriodScore.seasonalPoints | M2 (B7), M3 (C6) |
| `ProgressService`, `LeaderboardService` | M3 | — (read) | — |
| `AdminReviewService` | M3 | — (orchestrates) | — → calls M2 mark*, `RewardService`, `NotificationService` |
| `AdminEventService` | M3 | — (orchestrates) | — → calls `ChallengeService` (M2), `LobbyService` (M1) |
| `NotificationService` | M3 | Notification | M3 services |

---

## 3. Endpoint-group ownership

| Base path | Owner | Endpoints |
|---|:--:|---|
| `/api/users` | M1 | create player/admin, profile, memberships |
| `/api/kingdoms` | M1 | list, details, join (+ optional admin create) |
| `/api/lobbies` | M1 | create, list public, details, join |
| `/api/challenges` | M2 | **generate**, **select**, list, details, lobby-generate, lobby-attach (+ optional admin create) |
| `/api/submissions` | M2 | start, submit, list, get |
| `/api/verifications` | M2 | mock fitness, status |
| `/api/progress` | M3 | summary, kingdom progress, xp-history |
| `/api/leaderboards` | M3 | division-scoped board (+ optional king) |
| `/api/admin` | M3 | **events**, review queue, approve, reject (+ optional disable) |
| `/api/notifications` | M3 | list, mark-read (optional) |

---

## 4. Cross-service call graph

```
B2  AiChallengeService (M2) → persist Challenge → SubmissionService.startChallenge (M2)
B7  VerificationService (M2) → RewardService.awardReward (M3)
B10 AiChallengeService (M2) → LobbyService.attachChallenge (M1)
C6  AdminReviewService (M3) → SubmissionService.markApproved/markRejected (M2) → RewardService.awardReward (M3) → NotificationService (M3)
C-events AdminEventService (M3) → ChallengeService.createChallenge (M2) → LobbyService.createSeasonalLobby (M1)
A8  LobbyService (M1) → reads SubscriptionRepository (premium)
B1/B5 (M2) → reads KingdomMembershipRepository (M1's entity)
```

**Frozen interfaces (agree before coding):**
- `AiChallengeGenerator.generate(GenerateContext) → List<AiChallengeOption>` *(M2)*
- `RewardService.awardReward(membershipId, challengeProgressId, xpAwarded, seasonalPointsAwarded, totalPointsAwarded, source, externalId, challengeId) → RewardResult` *(M3; caller pre-scales by completionRate)*
- `SubmissionService.markApproved(progressId, VerifierType)` / `markRejected(progressId, RejectionReason)` *(M2)*
- `LobbyService.attachChallenge(lobbyId, challengeId)` / `createSeasonalLobby(...)` *(M1)*
- `ChallengeService.createChallenge(CreateChallengeRequest) → Challenge` *(M2)*
- `NotificationService.create(playerId, type, title, body, linkRef)` *(M3)*

---

## 5. Idempotency / anti-cheat keys (all enforced by `RewardService`)

- **Per run:** `ActivityRecord.challengeProgressId` UNIQUE → one award per `ChallengeProgress`.
- **Per external event:** `ActivityRecord(source, externalId)` UNIQUE. API: `source="MOCK_FITNESS"`, externalId=workout id. Admin: `source="ADMIN_REVIEW"`, externalId=`"progress:"+progressId`.

---

## 6. DTO ownership (request created per-flow; response owned by entity owner, read-only for others)

| Group | Request DTOs (owner) | Response DTOs (owner) |
|---|---|---|
| User/Kingdom/Lobby (M1) | CreatePlayer/Admin/UpdatePlayer/JoinKingdom/CreateLobby/JoinLobby | PlayerProfile/Kingdom/Membership/Lobby/LobbyMember Response |
| AI/Submission/Verify (M2) | Generate/Select/GenerateLobby/AttachLobby/Start/SubmitProof/MockFitness (+CreateChallenge opt.) | AiChallengeOption(s)/Challenge/ChallengeProgress/VerificationResult Response |
| XP/Leaderboard/Admin (M3) | Approve/Reject/CreateEvent | ProgressSummary/KingdomProgress/LeaderboardEntry/ActivityRecord/Event/Notification Response |

Cross-flow reads: M3 returns M2's `ChallengeProgressResponse` (admin queue/approve); M2 may read M3's
`ActivityRecordResponse`; M2/M3 read M1's `LobbyResponse`. Owner freezes the shape; consumers never modify it.
