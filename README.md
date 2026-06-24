# Kingdom (الممالك) 

> Kingdoms, memberships, leaderboards, player profiles, earned badges, and AI recommendations for a gamified Arabic-first self-improvement platform.

**🌐 Live API:** http://kingdom-env.eba-qz67sy59.eu-central-1.elasticbeanstalk.com  
**📖 API documentation:** [Postman published docs](https://documenter.getpostman.com/view/52784213/2sBXwwn7pE)  
**🎨 Design:** [Figma](https://www.figma.com/design/QUdd4Fr1vStZdvedZpRfyt/Kingdom?node-id=397-149&t=6DBbB8ELYWQBD5XK-1)

## Overview

**الممالك (Kingdom)** منصّة عربية لتطوير الذات بأسلوب الألعاب. ينضمّ اللاعب إلى **ممالك** متنوّعة (اللياقة، الصدقة، التطوّع، القراءة، الألعاب، الإيمان، المعرفة، التغذية، البرمجة)، ويخوض **تحدّيات يولّدها الذكاء الاصطناعي**، وعليه أن **يُثبت** إكمال كل تحدٍّ عبر تحقّق حقيقي — أنشطة Strava، أو تبرّعات بنكية عبر المصرفية المفتوحة (Neotek)، أو شهادات تطوّع يراجعها الذكاء الاصطناعي، أو اختبارات على واتساب، أو تحليل صور الوجبات، أو وقت اللعب والإنجازات على Steam، أو مساهمات GitHub. التحدّيات المُثبَتة تمنح **نقاط خبرة (XP)** ترفع **درجة اللاعب** (من D3 إلى D1) وتُكسبه **أوسمة**؛ كما **يتنافس اللاعبون في لوبيات** (يفتح اشتراك Premium عبر LemonSqueezy إنشاءها).

**الممالك (Kingdom)** is an Arabic-first, gamified self-improvement platform. A player joins different **kingdoms** (Fitness, Charity, Volunteering, Reading, Gaming, Faith, Knowledge, Nutrition, Programming) and takes on **AI-generated challenges**, then has to **prove** each one through real verification — Strava activities, bank donations over Open Banking (Neotek), volunteer certificates reviewed by AI, WhatsApp quizzes, meal-photo analysis, Steam playtime & achievements, or GitHub contributions. Verified challenges grant **XP** that raises the player's **division** (from D3 up to D1) and earns them **badges**; players also **compete in lobbies** (a Premium subscription via LemonSqueezy unlocks creating them).

## System class diagram

Describes the whole Kingdom system (15 entities), shared across the team; the modules in this README are Shahad's.

![Class diagram — Kingdom system](docs/class-diagram.png)

## Use case diagram

![Use case diagram — Kingdoms System](docs/use-case-diagram.png)

## My area: Kingdom, KingdomMembership, Badge, PlayerBadge, PeriodScore and ChallengeQuestion

This subsystem is where a player **lives inside the kingdoms**. It serves browsing and joining **kingdoms**, the **AI kingdom recommendation** that matches a player's interests to a kingdom, per-kingdom **membership** with all the player's standing — **XP, streak, division, badge, rank, land control, and progress to the next division** — and the **leaderboards** (by period, by division, or both). It also owns the **player profile** and the **AI-written performance report**, the player's **earned badges**, and the **seasonal period-scores**. Finally it owns the **challenge-question quizzes**, including the **WhatsApp quiz** that grades a player's answers by message. Kingdoms owned here: **Reading, Gaming, Faith**.

## AI in Kingdom

Two AI features in this area are player-facing. The **kingdom recommendation** reads a player's stated interests and suggests the kingdom that fits them best. The **AI player report** generates a personalised, plain-Arabic performance summary and emails it to the player. The **Reading, Gaming, and Faith** kingdoms run on **AI-generated challenges** (shared `AiService`, OpenAI gpt-5.5) and are verified through **quizzes** — reading-comprehension questions sourced from **Google Books**, **Faith / Quran tafsir** quizzes delivered and graded over **WhatsApp**, and **Steam** playtime/achievement checks for Gaming. XP is always awarded by our own code, never the model.

## My extra endpoints

Base URL `http://localhost:8080` (local) or the live deployment, all under `/api/v1`. Auth is HTTP Basic.

| Method | Path | What it does |
|---|---|---|
| GET | `/kingdom/get` | List all kingdoms. |
| GET | `/kingdom/get/{id}` | One kingdom's details. |
| POST | `/kingdom/ai-recommendation` | AI recommends the kingdom that fits the player's interests. |
| GET | `/kingdom/{kingdomId}/leaderboard/period/{period}` | Leaderboard for a kingdom by time period. |
| GET | `/kingdom/{kingdomId}/leaderboard/division/{division}` | Leaderboard filtered to one division. |
| GET | `/kingdom/{kingdomId}/leaderboard/period/{period}/division/{division}` | Leaderboard by period and division. |
| GET | `/kingdom/{kingdomId}/land-control/{division}` | Land-control summary for a kingdom + division. |
| POST | `/kingdom-membership/join/{kingdomId}` | Join a kingdom. |
| DELETE | `/kingdom-membership/leave/{kingdomId}` | Leave a kingdom. |
| GET | `/kingdom-membership/{kingdomId}/membership-id` | Resolve my membership id for a kingdom. |
| GET | `/kingdom-membership/{kingdomId}/member-xp` | My XP in a kingdom. |
| GET | `/kingdom-membership/{kingdomId}/member-streak` | My streak in a kingdom. |
| GET | `/kingdom-membership/{kingdomId}/member-divison` | My division in a kingdom. |
| GET | `/kingdom-membership/{kingdomId}/member-rank` | My leaderboard rank in a kingdom. |
| GET | `/kingdom-membership/{kingdomId}/member-land-percentage` | % of land I control in a kingdom. |
| GET | `/kingdom-membership/{kingdomId}/division-progress` | My progress toward the next division. |
| GET | `/kingdom-membership/{kingdomId}/xp-need-to-higher-rank` | XP I still need for the next rank. |
| GET | `/kingdom-membership/{kingdomId}/number-of-completed-challenges` | Count of challenges I completed in a kingdom. |
| POST | `/player/ai-report` | Generate + email my AI performance report. |
| GET | `/player/summary` | My activity & stats summary. |
| GET | `/player/best-kingdom` | My best-performing kingdom. |
| GET | `/player/kingdoms` | The kingdoms I participate in. |
| GET | `/player/highest-streak` | My highest streak across kingdoms. |
| GET | `/player-badge/player-badges` | The badges I've earned. |
| GET | `/player-badge/{kingdomId}/member-badges` | The badges I've earned in one kingdom. |
| POST | `/challenge-question/whatsapp/webhook` | Inbound WhatsApp quiz answer (Twilio) — grades the reply. |
| GET | `/subscription/days-left` | Days left on my subscription. |
| GET | `/lobby/my-finished` | The lobbies I've finished. |
| GET | `/lobby/{lobbyId}/member-count` | How many members a lobby has. |

## Tech stack

Java 17 · Spring Boot 4.0.6 · Spring Web · Spring Data JPA (Hibernate) · MySQL · Lombok · Maven · OpenAI (gpt-5.5) · LemonSqueezy · n8n · GitHub · AI Vision · Stream ·Strava · Google Books · Tafsir API · Quran Cloud API · Neotek · Twilio (WhatsApp) · Mailtrap.

## Run it

```bash
# MySQL on localhost with a database named `Data`, then:
mvn spring-boot:run
```

- Local base URL: `http://localhost:8080/api/v1`
- Live deployment: `http://kingdom-env.eba-qz67sy59.eu-central-1.elasticbeanstalk.com/api/v1`
- API documentation (Postman): https://documenter.getpostman.com/view/52784213/2sBXwwn7pE
- Provide secrets as env vars (or a git-ignored `src/main/resources/application-local.properties`): `OPENAI_API_KEY`, `TWILIO_ACCOUNT_SID`/`TWILIO_AUTH_TOKEN`, `MAILTRAP_API_TOKEN`, `STEAM_*`, `GOOGLE_BOOKS_*`.
- For deployment set `SPRING_JPA_HIBERNATE_DDL_AUTO=update` and `DEMO_SEED_ENABLED=false`.

## Team

This project — built by  **Shahad**, **Anas**, and **Maysun**. The modules documented in this README belong to **Shahad** (kingdoms, membership, leaderboards, profile, badges, challenge-question quizzes — Reading / Gaming / Faith).
