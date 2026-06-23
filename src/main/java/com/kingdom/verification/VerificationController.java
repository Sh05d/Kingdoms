package com.kingdom.verification;

import com.kingdom.API.ApiResponse;
import com.kingdom.Config.CustomUserDetails;
import com.kingdom.Service.ChallengeProgressService;
import com.kingdom.Service.LobbyInviteService;
import com.kingdom.Service.WhatsAppService;
import com.kingdom.Service.APIService.ChallengeQuestionWhatsappService;
import com.kingdom.Service.APIService.EmailService;
import com.kingdom.Service.StreakService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dev/testing endpoints so the Charity (Neotek), Fitness (Strava) and Volunteer (OpenAI PDF) flows can be
 * driven from Postman / WhatsApp. They call the REAL external services. (Player auth is added before final;
 * the WhatsApp webhook is protected by an optional Twilio signature check instead — see WhatsAppService.)
 */
@RestController
@RequestMapping("/api/v1/verify")
@RequiredArgsConstructor
public class VerificationController {

    private final NeotekClient neotekClient;
    private final CharityVerificationService charityVerificationService;
    private final StravaClient stravaClient;
    private final StravaConnectService stravaConnectService;
    private final FitnessVerificationService fitnessVerificationService;
    private final VolunteerVerificationService volunteerVerificationService;
    private final WhatsAppService whatsAppService;
    private final ChallengeProgressService challengeProgressService;
    // The Reading/Faith WhatsApp quiz handler — so this ONE inbound webhook serves both flows (PDF -> volunteer,
    // plain text -> quiz answer). Avoids the single-Twilio-webhook collision with Shahad's quiz endpoint.
    private final ChallengeQuestionWhatsappService challengeQuestionWhatsappService;
    // Welcome-email sender (Mailtrap). The REAL trigger is Maysun's verify-phone flow; this controller only
    // exposes a dev test endpoint so the Mailtrap wiring can be checked now.
    private final EmailService emailService;
    // Streak keeper (Anas): actively drops a per-kingdom streak after a missed day + warns ~6h before.
    private final StreakService streakService;
    // Private-lobby invite handler: a قبول/رفض button tap on this same inbound webhook joins or declines.
    private final LobbyInviteService lobbyInviteService;

    // ---- Charity (Neotek Open Banking) -------------------------------------------------------------

    /** Consent step 1 (the analogue of Strava's /fitness/connect): returns the Neotek bank CONSENT URL the player
     *  opens in a browser to authorise on their own bank, plus the accountsLinkId — a real redirect, not a direct link. */
    @PostMapping("/charity/link")
    public Object charityLink(@RequestParam(defaultValue = "20112") String psuId,
                              @RequestParam(required = false) String financialInstitutionId) {
        String bank = (financialInstitutionId == null || financialInstitutionId.isBlank())
                ? neotekClient.defaultFinancialInstitutionId() : financialInstitutionId;
        JsonNode result = neotekClient.createAccountsLink(bank, psuId, null, null);
        if (result == null) {
            return new ApiResponse("Neotek is off, unconfigured, or the link call failed");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("psuId", psuId);
        out.put("financialInstitutionId", bank);
        out.put("accountsLinkId", result.path("Data").path("AccountsLinkId").asText(""));
        out.put("authorizeUrl", result.path("Data").path("RedirectionURL").asText(""));
        out.put("note", "Open authorizeUrl in a browser and approve on your bank to authorise reading your donations.");
        return out;
    }

    // Charity LOBBY: hand-enter a member's donation (SAR) so the lobby can rank by total donated. The Neotek
    // sandbox has no real charity transactions, so the host records amounts here, then resolves the lobby.
    @PostMapping("/charity/manual-donate/{kingdomMembershipId}/{challengeId}")
    public Object charityManualDonate(@PathVariable Integer kingdomMembershipId,
                                      @PathVariable Integer challengeId,
                                      @RequestParam Integer amountSar) {
        challengeProgressService.recordManualCharityDonation(kingdomMembershipId, challengeId, amountSar);
        return new ApiResponse("Recorded " + amountSar + " SAR for membership " + kingdomMembershipId
                + " on challenge " + challengeId + " — resolve the lobby to rank by total donated.");
    }

    @GetMapping("/charity/accounts")
    public Object charityAccounts(@RequestParam(defaultValue = "20112") String psuId) {
        JsonNode result = neotekClient.listAccounts(psuId);
        return result == null ? new ApiResponse("Neotek is off or the call failed") : result.path("Data");
    }

    @GetMapping("/charity/transactions")
    public Object charityTransactions(@RequestParam(defaultValue = "20112") String psuId) {
        JsonNode result = neotekClient.listTransactions(psuId);
        return result == null ? new ApiResponse("Neotek is off or the call failed") : result.path("Data");
    }

    /** DEMO HELPER: simulate a donation for this PSU so the next /charity/check (or a charity finish) can PASS
     *  (the real Neotek sandbox has no charity transactions). Clearly a testing shortcut. */
    @PostMapping("/charity/donate")
    public Object charityDonate(@RequestParam(defaultValue = "20112") String psuId,
                                @RequestParam(defaultValue = "Ehsan") String charityName,
                                @RequestParam(defaultValue = "100") int amountSar) {
        charityVerificationService.recordDonation(psuId, charityName, amountSar);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("psuId", psuId);
        out.put("simulatedDonation", Map.of("charity", charityName, "amountSar", amountSar));
        return out;
    }

    @GetMapping("/charity/check")
    public Object charityCheck(@RequestParam(defaultValue = "20112") String psuId,
                               @RequestParam(required = false) String charityName,
                               @RequestParam(defaultValue = "1") int minAmountSar,
                               @RequestParam(defaultValue = "3650") int fromDays) {
        boolean donated = charityVerificationService.hasDonated(psuId, charityName, minAmountSar,
                LocalDateTime.now().minusDays(fromDays), LocalDateTime.now());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("psuId", psuId);
        out.put("charityName", charityName == null ? "(any)" : charityName);
        out.put("minAmountSar", minAmountSar);
        out.put("donated", donated);
        return out;
    }

    // ---- Fitness (Strava) --------------------------------------------------------------------------

    /** Per-player consent step 1: returns the Strava authorize URL for this player to open in a browser. */
    @GetMapping("/fitness/connect")
    public Object fitnessConnect(@AuthenticationPrincipal CustomUserDetails me) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("playerId", me.getId());
        out.put("authorizeUrl", stravaConnectService.authorizeUrlForPlayer(me.getId()));
        out.put("note", "Open authorizeUrl in a browser and approve on Strava; you'll be redirected to /fitness/callback which links your account.");
        return out;
    }

    /** Per-player consent step 2: Strava redirects here with ?code&state; we store the player's refresh token. */
    @GetMapping("/fitness/callback")
    public Object fitnessCallback(@RequestParam(required = false) String code,
                                  @RequestParam(required = false) String state,
                                  @RequestParam(required = false) String error) {
        return stravaConnectService.handleCallback(code, state, error);
    }

    @GetMapping("/fitness/activities")
    public Object fitnessActivities(@RequestParam(defaultValue = "30") int fromDays) {
        long after = LocalDateTime.now().minusDays(fromDays).atZone(ZoneId.systemDefault()).toEpochSecond();
        long before = LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond();
        JsonNode result = stravaClient.listActivities(after, before);
        if (result == null || !result.isArray()) {
            return new ApiResponse("Strava is off, unconfigured, or the call failed");
        }
        // Lean view: Strava sends ~40 fields per activity (most null/irrelevant). Keep only what a player
        // cares about so the response is readable.
        List<Map<String, Object>> activities = new ArrayList<>();
        for (JsonNode a : result) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", a.path("name").asText(""));
            item.put("type", a.path("sport_type").asText(a.path("type").asText("")));
            item.put("distanceMeters", a.path("distance").asDouble(0));
            item.put("movingTimeSeconds", a.path("moving_time").asInt(0));
            item.put("date", a.path("start_date_local").asText(""));
            activities.add(item);
        }
        return activities;
    }

    @GetMapping("/fitness/check")
    public Object fitnessCheck(@RequestParam(defaultValue = "distance_meters") String metricKey,
                               @RequestParam(defaultValue = "5000") int targetValue,
                               @RequestParam(required = false) String sportType,
                               @RequestParam(defaultValue = "30") int fromDays) {
        boolean reached = fitnessVerificationService.hasReached(metricKey, targetValue, sportType,
                LocalDateTime.now().minusDays(fromDays), LocalDateTime.now());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("goal", friendlyGoal(metricKey, targetValue, sportType));
        out.put("reached", reached);
        out.put("message", reached ? "✅ Goal reached!" : "❌ Not reached yet — keep going!");
        return out;
    }

    /** Turn the internal metricKey/target into a sentence a normal user can read. */
    private String friendlyGoal(String metricKey, int targetValue, String sportType) {
        String base;
        switch (metricKey == null ? "" : metricKey) {
            case "distance_meters":     base = "Cover " + targetValue + " meters";            break;
            case "moving_time_seconds": base = "Stay active for " + targetValue + " seconds"; break;
            case "activity_count":      base = "Complete " + targetValue + " activities";      break;
            default:                    base = metricKey + " ≥ " + targetValue;            break;
        }
        return (sportType == null || sportType.isBlank()) ? base : base + " (" + sportType + " only)";
    }

    // ---- Volunteer (OpenAI PDF certificate check) --------------------------------------------------

    /**
     * Postman path: upload a certificate PDF (form field "file") + optional "activity" + optional "progressId".
     * If progressId is given and the AI approves, that challenge run is completed (VERIFIED + XP).
     */
    @PostMapping(value = "/volunteer/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object volunteerUpload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(required = false) String activity,
                                  @RequestParam(required = false) Integer progressId,
                                  @RequestParam(required = false) String from) {
        if (file == null || file.isEmpty()) {
            return new ApiResponse("Attach a PDF in the form field 'file'");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            return new ApiResponse("Could not read the uploaded file");
        }

        // Phone-based completion (same path the WhatsApp webhook uses): match the player by phone and complete
        // their active volunteer challenge on approval.
        if (from != null && !from.isBlank()) {
            return challengeProgressService.completeVolunteerByPhone(from, bytes, file.getOriginalFilename());
        }

        Map<String, Object> result = volunteerVerificationService.verifyCertificate(
                bytes, file.getOriginalFilename(), activity);

        // Tie it to a challenge run: if approved, complete that run (award XP).
        if (progressId != null && Boolean.TRUE.equals(result.get("approved"))) {
            challengeProgressService.completeVerifiedRun(progressId);
            result.put("challengeCompleted", true);
        }
        return result;
    }

    /**
     * WhatsApp path (Twilio webhook). Point Twilio's "WHEN A MESSAGE COMES IN" at:
     *   https://YOUR-NGROK/api/v1/verify/volunteer/whatsapp  (POST).
     * Protected by an optional Twilio signature check (twilio.validate-signature).
     */
    @PostMapping(value = "/volunteer/whatsapp", produces = MediaType.APPLICATION_XML_VALUE)
    public String volunteerWhatsapp(@RequestParam Map<String, String> allParams,
                                    @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
                                    HttpServletRequest request) {
        if (!whatsAppService.passesSignatureCheck(publicUrl(request), allParams, signature)) {
            return twiml("Unauthorized request.");
        }
        String numMedia = allParams.getOrDefault("NumMedia", "0");
        String mediaUrl = allParams.get("MediaUrl0");
        String contentType = allParams.getOrDefault("MediaContentType0", "");
        boolean hasMedia = !"0".equals(numMedia) && mediaUrl != null;
        boolean hasPdf = hasMedia && contentType.toLowerCase().contains("pdf");
        boolean hasImage = hasMedia && contentType.toLowerCase().startsWith("image/");
        if (hasImage) {
            // Inbound food image -> NUTRITION submission over WhatsApp (reuses the volunteer-PDF download +
            // phone->player->active-run pattern). Replies via the returned TwiML; must NOT fall through to quiz.
            byte[] image = whatsAppService.downloadMedia(mediaUrl);
            if (image == null) {
                return twiml("Sorry, I couldn't download your image. Please try sending it again.");
            }
            return twiml(challengeProgressService.completeNutritionByPhone(
                    allParams.get("From"), image, contentType));
        }
        if (!hasPdf) {
            // No PDF -> this ONE inbound webhook serves three flows, by what came in:
            //   - a قبول/رفض button tap -> private-lobby invite (accept = join + hype, decline = reject)
            //   - any other text/tap     -> WhatsApp quiz answer (Reading/Faith/Knowledge)
            // All handlers reply via OUTBOUND WhatsApp, so we return EMPTY TwiML (echoing would double-send).
            // A قبول/رفض tap can arrive in ButtonPayload (the button id), ButtonText (its title), or Body (the
            // echoed reply) — Twilio fills a different one per template, so check ALL three. (The old code used only
            // the first non-blank field, so a custom button id hid the "قبول" title and the tap fell through to the quiz.)
            java.util.List<String> fields = java.util.List.of(
                    allParams.getOrDefault("ButtonPayload", ""),
                    allParams.getOrDefault("ButtonText", ""),
                    allParams.getOrDefault("Body", ""));
            boolean accept = fields.stream().map(String::trim)
                    .anyMatch(s -> s.equalsIgnoreCase("ACCEPT") || s.equals("قبول"));
            boolean decline = fields.stream().map(String::trim)
                    .anyMatch(s -> s.equalsIgnoreCase("DECLINE") || s.equals("رفض"));
            if (accept || decline) {
                lobbyInviteService.handleWhatsappInviteResponse(allParams.get("From"), accept);
            } else {
                challengeQuestionWhatsappService.handleIncomingAnswer(allParams.get("From"), allParams.get("Body"));
            }
            return twiml("");
        }
        byte[] pdf = whatsAppService.downloadMedia(mediaUrl);
        if (pdf == null) {
            return twiml("Sorry, I couldn't download your PDF. Please try sending it again.");
        }
        // Match the sender to their account + active volunteer challenge, verify, and complete it on approval.
        Map<String, Object> result = challengeProgressService.completeVolunteerByPhone(
                allParams.get("From"), pdf, "whatsapp.pdf");
        boolean approved = Boolean.TRUE.equals(result.get("approved"));
        String verdict = (approved ? "✅ تم قبول شهادتك" : "❌ لم تُقبل بعد")
                + " (الدرجة " + result.get("matchScore") + "). " + result.get("reason");
        if (Boolean.TRUE.equals(result.get("challengeCompleted"))) {
            verdict += " 🎉 \"" + result.get("challengeTitle") + "\" completed — XP awarded!";
        } else if (result.get("note") != null) {
            verdict += " (" + result.get("note") + ")";
        }
        return twiml(verdict);
    }

    // DEV/TEST: fire the welcome email (the same one Maysun's verify-phone flow should send on success) so the
    // Mailtrap wiring can be checked from Postman. Needs mailtrap.enabled=true + a real token in local props.
    // Remove (or fold into the real verify-phone flow) before final submission.
    @GetMapping("/test-welcome-email")
    public ApiResponse testWelcomeEmail(@RequestParam String to,
                                        @RequestParam(required = false) String name) {
        boolean sent = emailService.sendWelcome(to, name);
        return new ApiResponse(sent
                ? "Welcome email sent to " + to
                : "Email NOT sent (check mailtrap.enabled=true, a real token, and the address).");
    }

    // STREAK KEEPER (Anas). The per-kingdom daily streak drops the day after a missed day, and the player is
    // warned ~6h before losing it. The real schedule runs at 18:00 (warn) + 00:05 (reset); these endpoints
    // trigger it on demand for the demo (you can't wait for the cron).
    @PostMapping("/streak/run")
    public Object streakRun() {
        return streakService.runNow();
    }

    // DEMO: send the "your streak ends in 6 hours" WhatsApp for one player+kingdom on demand.
    @PostMapping("/streak/warn/{playerId}/{kingdomId}")
    public ApiResponse streakWarn(@PathVariable Integer playerId, @PathVariable Integer kingdomId) {
        streakService.previewWarning(playerId, kingdomId);
        return new ApiResponse("Streak at-risk warning sent (needs the player's phone + Twilio on).");
    }

    // Reconstruct the public URL Twilio called (behind ngrok/proxy it sends X-Forwarded-* headers).
    private String publicUrl(HttpServletRequest req) {
        String proto = req.getHeader("X-Forwarded-Proto");
        String host = req.getHeader("X-Forwarded-Host");
        if (proto != null && host != null) {
            return proto + "://" + host + req.getRequestURI();
        }
        return req.getRequestURL().toString();
    }

    // Wrap a message in Twilio's TwiML so it's sent back to the WhatsApp user as a reply.
    private String twiml(String message) {
        if (message == null || message.isBlank()) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response></Response>"; // empty -> Twilio sends no reply
        }
        String safe = message
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Message>" + safe + "</Message></Response>";
    }
}
