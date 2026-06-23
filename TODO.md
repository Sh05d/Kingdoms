# Kingdom — Deferred features / backlog (Anas's modules)

Things intentionally left for later so they don't get lost. None of these block the demo.

## Volunteer certificate anti-cheat (agreed: later)
Today the AI only checks *"is this a genuine-looking volunteer certificate?"* — not that it's **the player's**,
for **this** challenge, from the **right time**. A player could currently submit someone else's old certificate,
and re-submit the same PDF repeatedly to farm XP. Add before final:

- [ ] **Date check** — the certificate's issue date must fall within the challenge's window.
      Approach: have `VolunteerVerificationService` return a structured `issuedDate`; reject if outside the window.
      Make it a configurable, lenient default (e.g. `volunteer.cert-max-age-days=365`) and/or tie it to the
      challenge period (DAILY→today, WEEKLY→7d, …). (Demo certs are old, so keep the default lenient.)
- [ ] **Hours ≥ target** — return a structured `hours`; require it ≥ `challenge.targetValue`.
- [ ] **No reuse** — hash the PDF bytes (or read the cert's QR/id); store used hashes; reject duplicates.
- [ ] **Name match** — the volunteer name on the cert must match the player's profile name. (Strongest, but
      finicky with spelling; would reject the current "Zita Marco" demo cert.)

## Other known deferred items
- [ ] **Security final pass** (CLAUDE.md defers to final stage): enable Spring Security, real per-player auth,
      protect `/verify/*` + admin endpoints, and make the Strava OAuth `state` a random single-use server token
      (currently it's the raw playerId — account-linking CSRF).
- [ ] **WhatsApp webhook router** (needed at the team merge): one inbound `/whatsapp` endpoint that routes by
      message type — PDF/document → volunteer verify; plain text → teammate's onboarding flow. The Twilio
      sandbox only allows ONE "when a message comes in" URL, so the two flows currently can't coexist.
- [ ] **Lenient phone matching** — normalize numbers (compare last ~9 digits) so `+966…` / `966…` / `0…` all
      match, instead of the current exact-E.164 match in `completeVolunteerByPhone`.
- [ ] **Volunteer activity tie-in** — pass the challenge title/description as the AI's "activity to match"
      everywhere (the WhatsApp path currently passes the filename).

## Teammate-owned gaps (for the 9-kingdom merge, not Anas's to fix)
- READING auto-passes at finish (verificationSource `GOOGLE_BOOKS` has no dispatch branch) and the quiz answers
  are never checked anywhere.
- FAITH generation is a stub (`return ""`).
- `finishChallenge`'s `else → passed = true` auto-passes any unknown verificationSource.
- Source-string dispatch is brittle (exact-string; a typo/case drift silently auto-passes).
- STEAM failure sets terminal `REJECTED` instead of retry-able `NOT_COMPLETED` like the others.
