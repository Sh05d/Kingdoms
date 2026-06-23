# Entity Mapping — spec names → final implementation names

This project follows the **Master Spec** (`docs/Kingdom-Master-Spec.md`) as the source of truth for
the data model. The `CLAUDE.md` build strategy lists some entities under *illustrative* names
("including but not limited to ..."). This file reconciles those names with what is actually
implemented in `com.kingdom.Model`, so the 3-member team is never confused by a renamed concept.

> Rule of thumb: where the build-strategy name and the spec name differ, the **spec name wins**
> and is what exists in code. The one deliberate exception is the role enum value (see §3).

---

## 1. Entity name mapping

| Build-strategy / original name | Final entity (in `com.kingdom.Model`) | Notes |
|---|---|---|
| `UserKingdomProgress` | **`KingdomMembership`** | The Player↔Kingdom bridge; holds per-kingdom `xp` / `level`; derives `division()`. |
| `ChallengeSubmission` | **`ChallengeProgress`** + **`ActivityRecord`** | `ChallengeProgress` is the per-run state machine (join→submit→verify); `ActivityRecord` is the verified evidence. `ActivityRecord.challengeProgressId` links the award back to the exact run. |
| `XPTransaction` | **`ActivityRecord`** | The reward ledger — now **3 currencies** (`xpAwarded`/`seasonalPointsAwarded`/`totalPointsAwarded`), written only by `RewardService`; `challengeProgressId` (UNIQUE) ties an award to its run (no duplicate award). |
| `VerificationAttempt` | **`ChallengeProgress.attempts`** (+ `ActivityRecord`) | Attempts are a counter on the run; evidence rows live in `ActivityRecord`. |
| `Territory` | **`HexTile`** | One cell on a kingdom's shared hex grid; `ownerMembershipId` = all-time owner. |
| `UserBadge` | **`PlayerBadge`** | Awarded-badge instance (badges belong to the Player, not the account). |
| `LobbyParticipant` | **`LobbyMember`** | A participant inside a lobby. |
| `AdminEvent` | **`Lobby`** with `kind = SEASONAL` / `SPECIAL_EVENT` | No separate Event entity — the `Lobby` entity is reused (`kind`, `createdByAdmin`, `kingdomId`, free-text `category`). |
| `Subscription` | **`Subscription`** | Same. Slim mirror of provider status; no `Payment` table. |
| `User` | **`User`** | Same (account: identity, role, password hash). |
| `Kingdom` | **`Kingdom`** | Same. |
| `Challenge` | **`Challenge`** | Same. |
| `ConnectedAccount` | **`ConnectedAccount`** | Same. |
| `Notification` | **`Notification`** | Same. |
| `Lobby` | **`Lobby`** | Same (also reused for events, above). |
| `Badge` | **`Badge`** | Same (catalog). |

### Spec entities with no build-strategy alias (present because the spec splits concerns)
| Final entity | Why it exists |
|---|---|
| **`Player`** | The game profile, split 1–1 from `User`. `User` = account/auth/role; `Player` = display name, colour, prefs. Progression is per-kingdom, so there is **no** global XP on either. |
| **`PeriodScore`** | The leaderboard bucket (daily/weekly/monthly XP per membership); rank is computed, not stored. |
| **`LobbyInvite`** | A private-lobby invite by phone → WhatsApp. |

---

## 2. Enum mapping

| Build-strategy enum | Final enum(s) | Notes |
|---|---|---|
| `UserRole` | **`UserRole`** `{PLAYER, ADMIN}` | See §3 — value renamed from the spec's `USER`. |
| `KingdomType` | **`KingdomType`** | The 10 kingdoms. |
| `ChallengeStatus` | **`ProgressStatus`** `{JOINED, IN_PROGRESS, SUBMITTED, VERIFIED, REJECTED, EXPIRED}` | The challenge-run lifecycle. |
| `ChallengeType` | **`ChallengeScope`** `{SOLO, LOBBY}` (+ `Difficulty`, `Period`) | "Type" is covered by scope + difficulty + period; no single `ChallengeType` enum. |
| `SubmissionStatus` | **`ProgressStatus`** / **`VerificationStatus`** | Run state vs. evidence verification state. |
| `VerificationType` | **`VerifierType`** `{API, AI, ADMIN}` | Who verified. |
| `VerificationStatus` | **`VerificationStatus`** `{PENDING, VERIFIED, REJECTED}` | Same. |
| `Division` | **`Division`** `{D1, D2, D3}` | **Derived** from `KingdomMembership.xp`; never a stored column. |
| `Difficulty` | **`Difficulty`** | Same. |
| `XPTransactionType` | *(none)* | Dropped — `ActivityRecord` is the ledger; no transaction-type enum needed. |
| `NotificationType` | **`NotificationType`** | Same. |
| `LobbyStatus` | **`LobbyStatus`** | Same. |
| `BadgeType` | **`BadgeType`** | Same. |
| `SubscriptionStatus` | **`SubscriptionStatus`** | Same (+ `SubscriptionPlan`). |

Additional spec enums implemented (no build-strategy alias): `SubscriptionPlan`, `Period`,
`LobbyVisibility`, `LobbyKind`, `MemberRole`, `InviteChannel`, `InviteStatus`, `RejectionReason`,
`ConnectedProvider`.

---

## 3. The one intentional divergence from the spec: role value

- **Master Spec** models the account role as `UserRole {USER, ADMIN}`.
- **This project uses `UserRole {PLAYER, ADMIN}`** to match the two-role model stated in `CLAUDE.md`
  ("The system has two main user roles: `PLAYER`, `ADMIN`").

There is no behavioural difference — `PLAYER` is simply the renamed account role for a normal user.
The game profile is still the separate `Player` entity; `User.role` just records whether the account
is a normal player or an admin.

---

## 4. Relationship style (applies to every mapping above)

All associations are modelled as **plain `Integer` foreign-key fields** (`playerId`, `kingdomId`,
`membershipId`, `challengeId`, `lobbyId`, ...), not JPA `@ManyToOne` / `@OneToMany` graphs. This
matches both the spec's class diagram and `CLAUDE.md`'s "prefer simple foreign key fields" rule, and
keeps the schema flat and easy to explain during evaluation.

---

## 5. AI-first / 3-currency updates (later corrections)

| Was | Now | Why |
|---|---|---|
| `XpService` | **`RewardService`** | Awards **3 currencies** (XP + seasonal + total points), not just XP. Sole writer of rewards + `ActivityRecord`. |
| single XP currency | **XP + Seasonal Points + Total Points** | XP → level/division (permanent); `PeriodScore.seasonalPoints` → season tiles/leaderboard (resets); `KingdomMembership.totalPoints` → all-time tiles/leaderboard (permanent). |
| `PeriodScore.xp` | **`PeriodScore.seasonalPoints`** | Field renamed to match the season currency. |
| `KingdomType.SPECIAL_EVENTS` | **`KingdomType.OPEN_CHALLENGE`** | Open-category, lobby-only kingdom; exempt from the free-2-kingdom limit. |
| `InviteChannel {WHATSAPP, EMAIL}` | **`+ SMS`** | Twilio SMS is the primary private-lobby invite channel. |
| `InviteStatus {…}` | **`+ EXPIRED`** | Invite can lapse with no response. |

Other additive fields: `Challenge.metricKey/targetValue/safetyNotes/active` + `seasonalPointsReward/totalPointsReward`;
`ChallengeProgress.challengeStartAt/challengeEndAt/verifiedValue/completionRate/seasonal+totalPointsEarned`;
`ActivityRecord.challengeProgressId` + `seasonal+totalPointsAwarded`; `Lobby.division`; `LobbyInvite.inviteCode`.
