package com.kingdom.Service;
import com.kingdom.Config.AuthUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingdom.API.ApiException;
import com.kingdom.DTO.IN.ChallengeProgressIn;
import com.kingdom.DTO.IN.GithubSubmissionIn;
import com.kingdom.DTO.IN.KnowledgeSubmissionIn;
import com.kingdom.DTO.IN.KnowledgeAnswerIn;
import com.kingdom.Enums.ChallengeScope;
import com.kingdom.Enums.ConnectedProvider;
import com.kingdom.Enums.Period;
import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Enums.RejectionReason;
import com.kingdom.Model.Challenge;
import com.kingdom.Model.ChallengeProgress;
import com.kingdom.Model.ChallengeQuestion;
import com.kingdom.Model.ConnectedAccount;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Repository.ChallengeProgressRepository;
import com.kingdom.Repository.ChallengeQuestionRepository;
import com.kingdom.Repository.ChallengeRepository;
import com.kingdom.Repository.ConnectedAccountRepository;
import com.kingdom.Repository.KingdomMembershipRepository;
import com.kingdom.Repository.UserRepository;
import com.kingdom.verification.CharityVerificationService;
import com.kingdom.verification.FitnessVerificationService;
import com.kingdom.verification.VolunteerVerificationService;
import com.kingdom.Service.APIService.ChallengeQuestionWhatsappService;
import com.kingdom.Service.APIService.GithubService;
import com.kingdom.Service.APIService.SteamService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChallengeProgressService {


    private final ChallengeProgressRepository challengeProgressRepository;
    private final ChallengeRepository challengeRepository;
    // Maysun (Coding/Knowledge kingdom): GitHub commit/PR verification.
    private final GithubService githubService;
    // TEAMMATE (User/Kingdom flow owns KingdomMembership): resolve a player's membership on join, and write
    // XP/division/streak to it on a verified finish via ProgressRewardService.
    private final KingdomMembershipRepository kingdomMembershipRepository;
    // Anas-owned: the player's linked provider tokens (NEOTEK PSUId for charity verification).
    private final ConnectedAccountRepository connectedAccountRepository;
    // The kingdom verifiers that gate a finish. Strava + Neotek are pull-based (read external data at finish);
    // Volunteer is push-based (the player uploads a PDF), so it completes via the upload endpoint instead.
    private final FitnessVerificationService fitnessVerificationService;
    private final CharityVerificationService charityVerificationService;
    // Volunteer kingdom: AI PDF certificate check (used by the WhatsApp/phone volunteer-completion flow).
    private final VolunteerVerificationService volunteerVerificationService;
    // Match an inbound WhatsApp sender to their account by phone, to complete a volunteer challenge from a PDF.
    private final UserRepository userRepository;
    // Sends the "you finished a challenge!" hype WhatsApp on a verified finish (best-effort; never blocks finish).
    private final WhatsAppService whatsAppService;

    // Fallback Neotek PSU id used when a player has no linked NEOTEK account (demo).
    @Value("${neotek.demo-psu-id:20112}")
    private String demoPsuId;

    private final ConnectedAccountService connectedAccountService;
    private final ChallengeQuestionWhatsappService challengeQuestionWhatsappService;
    private final SteamService steamService;
    // Single owner of the XP + daily streak + division rules; shared with Shahad's WhatsApp quiz finish.
    private final ProgressRewardService progressRewardService;
    // Auto-resolve a lobby when its challenge gets finished+verified (first finisher wins).
    private final LobbyChallengeService lobbyChallengeService;
    private final ChallengeQuestionRepository challengeQuestionRepository;
    @Qualifier("openAiWebClient")
    private final WebClient openAiWebClient;


    public List<ChallengeProgress> getAllChallengeProgresses() {
        return challengeProgressRepository.findAll();
    }

    // Generic create. The NORMAL way to start a run is joinChallenge(...); this is for admin/testing.
    public void addChallengeProgress(ChallengeProgressIn in) {
        ChallengeProgress progress = new ChallengeProgress();
        progress.setStatus(in.getStatus());
        progress.setStartAt(in.getStartAt());
        progress.setFinishedAt(in.getFinishedAt());
        progress.setRejectionReason(in.getRejectionReason());
        progress.setVerifiedValue(in.getVerifiedValue());

        if (in.getChallengeId() != null) {
            Challenge challenge = challengeRepository.findChallengeById(in.getChallengeId());
            if (challenge == null) {
                throw new ApiException("Challenge not found");
            }
            progress.setChallenge(challenge);
        }
        if (in.getKingdomMembershipId() != null) {
            KingdomMembership membership =
                    kingdomMembershipRepository.findById(in.getKingdomMembershipId()).orElse(null);
            if (membership == null) {
                throw new ApiException("Kingdom membership not found");
            }
            progress.setKingdomMembership(membership);
        }
        challengeProgressRepository.save(progress);
    }

    public ChallengeProgress getChallengeProgressById(Integer id) {
        ChallengeProgress challengeProgress = challengeProgressRepository.findChallengeProgressById(id);
        if (challengeProgress == null) {
            throw new ApiException("ChallengeProgress not found");
        }
        return challengeProgress;
    }

    public void updateChallengeProgress(Integer id, ChallengeProgressIn in) {
        ChallengeProgress oldChallengeProgress = getChallengeProgressById(id);
        oldChallengeProgress.setStatus(in.getStatus());
        oldChallengeProgress.setStartAt(in.getStartAt());
        oldChallengeProgress.setFinishedAt(in.getFinishedAt());
        oldChallengeProgress.setRejectionReason(in.getRejectionReason());
        oldChallengeProgress.setVerifiedValue(in.getVerifiedValue());
        challengeProgressRepository.save(oldChallengeProgress);
    }

    public void deleteChallengeProgress(Integer id) {
        ChallengeProgress challengeProgress = getChallengeProgressById(id);
        challengeProgressRepository.delete(challengeProgress);
    }

    // ---- Challenge lifecycle: join -> finish ----

    // Join a challenge. Joining IS starting (there is no separate start step).
    // A player may join MULTIPLE challenges at the same time (no active-challenge limit).
    public Map<String, Object> joinChallenge(Integer playerId, Integer challengeId) {
        Challenge challenge = challengeRepository.findChallengeById(challengeId);
        if (challenge == null) {
            throw new ApiException("Challenge not found");
        }
        if (challenge.getKingdom() == null) {
            throw new ApiException("Challenge is not tied to a kingdom");
        }
        // Only join a challenge whose active window is currently open (null dates = always open). Grace margin:
        // an AI-generated challenge has startDate=now, but MySQL DATETIME rounds the fractional second UP, so a
        // join in that same second would wrongly see it as "future". Only reject a genuinely future-scheduled start.
        LocalDateTime now = LocalDateTime.now();
        if (challenge.getStartDate() != null && challenge.getStartDate().isAfter(now.plusMinutes(1))) {
            throw new ApiException("This challenge hasn't started yet");
        }
        if (challenge.getEndDate() != null && challenge.getEndDate().isBefore(now)) {
            throw new ApiException("This challenge has ended");
        }

        KingdomMembership membership =
                kingdomMembershipRepository.findByPlayer_IdAndKingdom_Id(playerId, challenge.getKingdom().getId());
        if (membership == null) {
            throw new ApiException("Player is not a member of this challenge's kingdom");
        }

        // One run per challenge per player: block re-joining a challenge that's already in progress or completed,
        // so a player can't finish several copies of the same challenge for repeated XP.
        for (ChallengeProgress existing : challengeProgressRepository.findAllByKingdomMembership_Player_IdAndStatusIn(
                playerId, List.of(ProgressStatus.JOINED, ProgressStatus.IN_PROGRESS, ProgressStatus.VERIFIED))) {
            if (existing.getChallenge() != null && challengeId.equals(existing.getChallenge().getId())) {
                throw new ApiException("You already have this challenge in progress or completed");
            }
        }

        ChallengeProgress progress = new ChallengeProgress();
        progress.setStatus(ProgressStatus.JOINED);
        progress.setStartAt(LocalDateTime.now()); // joining IS starting
        progress.setKingdomMembership(membership);
        progress.setChallenge(challenge);
        challengeProgressRepository.save(progress);

        // Hype the player to go finish what they just started (best-effort).
        notifyJoin(membership, challenge);

        // Return the progressId so the caller can finish/submit without a separate "resolve my id" step.
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "🎯 انضممت إلى التحدي \"" + challenge.getTitle() + "\". " + nextStepInstruction(challenge));
        out.put("progressId", progress.getId());
        return out;
    }

    /**
     * Finish a challenge: run the kingdom's REAL verification, and only award XP if it passes.
     *  - STRAVA              -> read the player's Strava activities and check the metric/target.
     *  - NEOTEK_OPEN_BANKING -> read the player's bank transactions and check for a donation >= target.
     *  - AI_PDF_MATCH        -> can't verify here (needs the uploaded PDF) -> use the volunteer upload endpoint.
     *  - anything else/null  -> no verifier wired -> counts as a pass.
     * On FAIL the run STAYS active (JOINED) with rejectionReason NOT_COMPLETED, so the player can do more and
     * finish again. The run is only lost on cancel or when its window expires.
     */
    @Transactional
    public String finishChallenge(Integer id) {
        ChallengeProgress progress = getChallengeProgressById(id);
        AuthUtil.requireSelfOrAdmin(progress.getKingdomMembership().getPlayer().getId());
        if (progress.getStatus() != ProgressStatus.JOINED) {
            throw new ApiException("Only a joined (active) challenge can be finished");
        }
        KingdomMembership membership = progress.getKingdomMembership();
        Challenge challenge = progress.getChallenge();

        String source = (challenge == null) ? null : challenge.getVerificationSource();
        int target = (challenge == null || challenge.getTargetValue() == null) ? 0 : challenge.getTargetValue();
        LocalDateTime from = windowFrom(progress);
        LocalDateTime now = LocalDateTime.now();

        boolean passed;
        if ("STRAVA".equals(source)) {
            // Count activities from the START OF THE DAY the player joined: a run done FOR the challenge counts —
            // even a manually-added one stamped a few seconds before they hit "join" — while runs from earlier
            // days don't complete a challenge they only just started. (The exact join moment was too strict.)
            LocalDateTime joinedAt = progress.getStartAt() != null ? progress.getStartAt() : now.minusYears(1);
            LocalDateTime fitnessFrom = joinedAt.toLocalDate().atStartOfDay();
            passed = fitnessVerificationService.hasReached(
                    challenge.getMetricKey(), target, null, fitnessFrom, now, resolveStravaRefreshToken(membership));
        } else if ("NEOTEK_OPEN_BANKING".equals(source)) {
            passed = charityVerificationService.hasDonated(resolvePsuId(membership), null, target, from, now);
        } else if ("AI_PDF_MATCH".equals(source)) {
            // Volunteer: WhatsApp the player to send their certificate PDF, which completes the run (see
            // completeVolunteerByPhone). We don't pass/fail here — the PDF arrives later over WhatsApp/upload.
            promptVolunteerCertificate(membership, challenge);
            return "Almost there! Send your volunteer certificate PDF over WhatsApp (we've messaged you), "
                    + "or upload it: POST /api/v1/verify/volunteer/upload?progressId=" + id;
        } else if (source != null && source.equalsIgnoreCase("WHATSAPP")) {
            // Shahad (Reading/Faith quiz): send the multiple-choice questions over WhatsApp; the run completes
            // when the player's answers come back (graded async), so we don't pass/fail here.
            challengeQuestionWhatsappService.sendChallengeQuestions(progress.getId());
            return "Quiz sent to your WhatsApp — answer the questions there to finish this challenge.";
        } else if (source != null && source.equalsIgnoreCase("STEAM")) {
            // Shahad (Gaming): Steam verification. A non-pass falls into the shared "keep JOINED" branch below.
            passed = steamCheck(progress);
        } else {
            passed = true; // no verifier for this source -> counts as a pass
        }

        if (!passed) {
            // Keep the run JOINED so the player can keep trying and finish again — never reject, never 400.
            progress.setRejectionReason(null);
            challengeProgressRepository.save(progress);
            return "Not verified yet — you haven't met the target. Your challenge is still in progress, keep going and finish again!";
        }
        markVerified(progress, membership);
        return "Challenge verified and completed — XP awarded";
    }

    /** Called by the volunteer upload endpoint after the AI approves the certificate, to complete that run. */
    @Transactional
    public void completeVerifiedRun(Integer progressId) {
        ChallengeProgress progress = getChallengeProgressById(progressId);
        if (progress.getStatus() != ProgressStatus.JOINED) {
            throw new ApiException("Only a joined (active) challenge can be completed");
        }
        markVerified(progress, progress.getKingdomMembership());
    }

    // Mark a run as PASSED: bump streak + set VERIFIED + award XP + recompute division. Shared by finish
    // (Strava/Neotek pass) and the volunteer upload (PDF approved).
    private void markVerified(ChallengeProgress progress, KingdomMembership membership) {
        // Lobby challenges are competition-only: NO XP/streak/division and no solo "you earned XP" hype.
        // Only solo (kingdom) challenges award XP; the lobby win/loss message comes from the resolver below.
        boolean isLobby = membership != null && membership.getPlayer() != null && progress.getChallenge() != null
                && lobbyChallengeService.isLobbyChallenge(progress.getChallenge().getId(), membership.getPlayer().getId());

        if (!isLobby) {
            // Streak + XP + division run BEFORE we mark VERIFIED, so updateStreak's "finished today?" check
            // excludes the run that is finishing right now.
            progressRewardService.applyVerifiedReward(progress, membership);
        }

        progress.setStatus(ProgressStatus.VERIFIED);
        progress.setRejectionReason(null); // clear any earlier failed-attempt note
        progress.setFinishedAt(LocalDateTime.now());
        challengeProgressRepository.save(progress);

        if (!isLobby) {
            // Solo hype (best-effort). For a lobby challenge the win/loss message is sent by the resolver instead.
            notifyFinish(membership, progress);
        }

        // If this challenge belongs to a lobby, auto-resolve it now (first finisher wins). Best-effort.
        lobbyChallengeService.onChallengeVerified(progress);
    }

    /**
     * Manually record a charity donation for a player's charity-challenge run. Charity LOBBIES are a
     * "highest donor" race, but the Neotek sandbox has no real charity transactions — so the host hand-enters
     * each member's donated amount, stored on verifiedValue, and the lobby resolver ranks by total donated.
     * No XP (lobby = competition only) and NO auto-resolve — the host resolves manually.
     */
    public void recordManualCharityDonation(Integer kingdomMembershipId, Integer challengeId, Integer amountSar) {
        if (amountSar == null || amountSar < 0) {
            throw new ApiException("amountSar must be >= 0");
        }
        KingdomMembership membership = kingdomMembershipRepository.findById(kingdomMembershipId)
                .orElseThrow(() -> new ApiException("Kingdom membership not found"));
        Challenge challenge = challengeRepository.findChallengeById(challengeId);
        if (challenge == null) {
            throw new ApiException("Challenge not found");
        }
        if (!"NEOTEK_OPEN_BANKING".equalsIgnoreCase(challenge.getVerificationSource())) {
            throw new ApiException("This is not a charity challenge");
        }

        List<ChallengeProgress> existing =
                challengeProgressRepository.findAllByKingdomMembershipAndChallenge(membership, challenge);
        ChallengeProgress progress = existing.isEmpty() ? new ChallengeProgress() : existing.get(0);
        if (progress.getStartAt() == null) {
            progress.setStartAt(LocalDateTime.now());
        }
        progress.setKingdomMembership(membership);
        progress.setChallenge(challenge);
        progress.setVerifiedValue(amountSar);          // donation amount (SAR) -> the lobby ranks by this
        progress.setStatus(ProgressStatus.VERIFIED);
        progress.setRejectionReason(null);
        progress.setFinishedAt(LocalDateTime.now());
        challengeProgressRepository.save(progress);
    }

    // Send the player a celebratory WhatsApp after a verified finish. Best-effort: guarded so any failure
    // (no phone, Twilio off/unconfigured, API error) is swallowed and never breaks the finish/XP flow.
    private void notifyFinish(KingdomMembership membership, ChallengeProgress progress) {
        try {
            if (membership == null || membership.getPlayer() == null) {
                return;
            }
            var player = membership.getPlayer();
            var user = player.getUser();
            if (user == null || user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                return;
            }
            String title = (progress.getChallenge() != null && progress.getChallenge().getTitle() != null)
                    ? progress.getChallenge().getTitle() : "تحديك";
            Integer reward = (progress.getChallenge() != null) ? progress.getChallenge().getXpReward() : null;
            int totalXp = membership.getTotalXP() == null ? 0 : membership.getTotalXP();
            int streak = membership.getStreak() == null ? 0 : membership.getStreak();
            String name = (player.getDisplayName() == null || player.getDisplayName().isBlank())
                    ? "بطل" : player.getDisplayName();
            String message = "🎉 أحسنت! أكملت التحدي \"" + title + "\" وحصلت على +"
                    + (reward == null ? 0 : reward) + " نقطة خبرة. رصيدك الآن " + totalXp + " نقطة"
                    + (streak > 0 ? " بسلسلة " + streak + " يوم متتالٍ 🔥" : "")
                    + ". واصل تألقك يا " + name + "! 👑";
            whatsAppService.sendMessage(user.getPhoneNumber(), message);
        } catch (Exception e) {
            // best-effort notification — swallow everything so a verified finish is never affected.
        }
    }

    // Hype the player over WhatsApp when they JOIN a challenge, to push them to finish it (best-effort).
    private void notifyJoin(KingdomMembership membership, Challenge challenge) {
        try {
            if (membership == null || membership.getPlayer() == null) {
                return;
            }
            var user = membership.getPlayer().getUser();
            if (user == null || user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                return;
            }
            String title = (challenge != null && challenge.getTitle() != null)
                    ? challenge.getTitle() : "تحديك";
            String name = (membership.getPlayer().getDisplayName() == null
                    || membership.getPlayer().getDisplayName().isBlank())
                    ? "بطل" : membership.getPlayer().getDisplayName();

            boolean isLobby = challenge != null
                    && lobbyChallengeService.isLobbyChallenge(challenge.getId(), membership.getPlayer().getId());
            String step = nextStepInstruction(challenge);
            String message;
            if (isLobby) {
                // Lobby challenges are competition-only — NO XP, just the race for the top score.
                message = "💪 انضممت لتحدي اللوبي \"" + title + "\" — نافِس على المركز الأول بأعلى نتيجة! "
                        + "لا نقاط خبرة، فقط المنافسة والمجد يا " + name + " ⚔️👑\n\n" + step;
            } else {
                Integer reward = (challenge != null) ? challenge.getXpReward() : null;
                message = "💪 انضممت! بدأت التحدي \"" + title + "\" — أكمله لتكسب +" + (reward == null ? 0 : reward)
                        + " نقطة خبرة يا " + name + "\n\n" + step;
            }
            whatsAppService.sendMessage(user.getPhoneNumber(), message);
        } catch (Exception e) {
            // best-effort
        }
    }

    // The player's next concrete action after joining, by challenge type — so the join message says HOW to finish,
    // not just "you joined". Used in both the join WhatsApp message and the join API response.
    private String nextStepInstruction(Challenge challenge) {
        if (challenge == null) return "";
        String src = challenge.getVerificationSource() == null ? "" : challenge.getVerificationSource().toUpperCase();
        String rule = challenge.getVerificationRule() == null ? "" : challenge.getVerificationRule().toUpperCase();

        if (rule.contains("FOOD_IMAGE")) {
            return "📸 خطوتك التالية: أرسل صورة وجبتك هنا على واتساب وسنحلّلها لك فوراً.";
        }
        if (src.equals("WHATSAPP")) { // WhatsApp quiz (nutrition is handled above by its FOOD_IMAGE rule)
            return "📝 خطوتك التالية: أنهِ التحدي من التطبيق وستصلك أسئلة الاختبار هنا على واتساب للإجابة عليها.";
        }
        if (src.equals("AI_PDF_MATCH")) {
            return "📄 خطوتك التالية: أرسل شهادة التطوع بصيغة PDF هنا على واتساب وسنتحقق منها.";
        }
        if (src.equals("STRAVA")) {
            return "🏃 خطوتك التالية: سجّل نشاطك الرياضي على Strava، ثم أنهِ التحدي من التطبيق.";
        }
        if (src.equals("STEAM")) {
            return "🎮 خطوتك التالية: حقّق هدف اللعبة على Steam، ثم أنهِ التحدي من التطبيق.";
        }
        if (src.equals("NEOTEK_OPEN_BANKING")) {
            return "💝 خطوتك التالية: نفّذ تبرعك، ثم أنهِ التحدي من التطبيق للتحقق.";
        }
        if (src.equals("GITHUB")) {
            return "💻 خطوتك التالية: ارفع التزاماتك (commits) على GitHub، ثم أرسل رابط المستودع من التطبيق.";
        }
        return "✅ خطوتك التالية: أكمل متطلبات التحدي ثم أنهِه من التطبيق.";
    }

    // Prompt the player over WhatsApp to send their volunteer certificate PDF (best-effort).
    private void promptVolunteerCertificate(KingdomMembership membership, Challenge challenge) {
        try {
            if (membership == null || membership.getPlayer() == null) {
                return;
            }
            var user = membership.getPlayer().getUser();
            if (user == null || user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
                return;
            }
            String title = (challenge != null && challenge.getTitle() != null)
                    ? challenge.getTitle() : "تحدي التطوع";
            whatsAppService.sendMessage(user.getPhoneNumber(),
                    "📄 لإتمام التحدي \"" + title + "\"، أرسل شهادة التطوع بصيغة PDF هنا وسنتحقق منها.");
        } catch (Exception e) {
            // best-effort
        }
    }

    /**
     * WhatsApp/phone volunteer completion: match the sender's phone to their account + active volunteer run,
     * verify the uploaded PDF, and complete the run (XP + hype) on approval. Returns the verification result
     * plus challengeCompleted / challengeTitle (or a note) for the reply. Used by the WhatsApp webhook and by
     * /verify/volunteer/upload?from=...
     */
    public Map<String, Object> completeVolunteerByPhone(String phone, byte[] pdf, String filename) {
        var user = (phone == null) ? null : userRepository.findUserByPhoneNumber(normalizePhone(phone));
        var player = (user == null) ? null : user.getPlayer();
        ChallengeProgress run = (player == null) ? null : findActiveVolunteerRun(player.getId());
        String firstName = (player != null && player.getDisplayName() != null && !player.getDisplayName().isBlank())
                ? player.getDisplayName().trim().split("\\s+")[0] : null;

        Map<String, Object> out = new LinkedHashMap<>(
                volunteerVerificationService.verifyCertificate(pdf, filename, firstName));
        boolean approved = Boolean.TRUE.equals(out.get("approved"));

        if (player == null) {
            out.put("challengeCompleted", false);
            out.put("note", "This WhatsApp number isn't linked to a Kingdom account.");
        } else if (run == null) {
            out.put("challengeCompleted", false);
            out.put("note", "No active volunteer challenge — join one first, then send your certificate.");
        } else if (approved) {
            completeVerifiedRun(run.getId()); // VERIFIED + XP + hype WhatsApp (via markVerified)
            out.put("challengeCompleted", true);
            out.put("challengeTitle", run.getChallenge().getTitle());
        } else {
            out.put("challengeCompleted", false);
            out.put("challengeTitle", run.getChallenge().getTitle());
        }
        return out;
    }

    // The player's active (JOINED/IN_PROGRESS) volunteer run (verificationSource AI_PDF_MATCH), or null.
    private ChallengeProgress findActiveVolunteerRun(Integer playerId) {
        ChallengeProgress found = null;
        for (ChallengeProgress p : challengeProgressRepository.findAllByKingdomMembership_Player_IdAndStatusIn(
                playerId, List.of(ProgressStatus.JOINED, ProgressStatus.IN_PROGRESS))) {
            if (p.getChallenge() != null && "AI_PDF_MATCH".equals(p.getChallenge().getVerificationSource())) {
                found = p; // keep the latest matching run
            }
        }
        return found;
    }

    // The player's active (JOINED/IN_PROGRESS) nutrition run (verificationRule FOOD_IMAGE_ANALYSIS), or null.
    private ChallengeProgress findActiveNutritionRun(Integer playerId) {
        ChallengeProgress found = null;
        for (ChallengeProgress p : challengeProgressRepository.findAllByKingdomMembership_Player_IdAndStatusIn(
                playerId, List.of(ProgressStatus.JOINED, ProgressStatus.IN_PROGRESS))) {
            if (p.getChallenge() != null && "FOOD_IMAGE_ANALYSIS".equals(p.getChallenge().getVerificationRule())) {
                found = p; // keep the latest matching run
            }
        }
        return found;
    }

    /**
     * WhatsApp/phone nutrition completion: match the sender's phone to their account + active nutrition run
     * (FOOD_IMAGE_ANALYSIS), analyze the uploaded food image, and complete the run (XP) on approval. Mirrors
     * completeVolunteerByPhone. Returns the Arabic verification result string for the reply. Used by the
     * WhatsApp webhook image branch.
     */
    public String completeNutritionByPhone(String phone, byte[] imageBytes, String contentType) {
        var user = (phone == null) ? null : userRepository.findUserByPhoneNumber(normalizePhone(phone));
        var player = (user == null) ? null : user.getPlayer();
        ChallengeProgress run = (player == null) ? null : findActiveNutritionRun(player.getId());

        if (player == null) {
            return "⚠️ هذا الرقم غير مرتبط بحساب في المملكة.";
        }
        if (run == null) {
            return "⚠️ لا يوجد تحدي تغذية نشط — انضمي لتحدي تغذية أولاً ثم أرسلي صورة الوجبة هنا.";
        }
        return submitNutritionImageFromWhatsApp(run.getId(), imageBytes, contentType);
    }

    // Strip the "whatsapp:" prefix Twilio puts on the From number so it matches the stored phone.
    private String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String p = raw.trim();
        if (p.toLowerCase().startsWith("whatsapp:")) {
            p = p.substring("whatsapp:".length()).trim();
        }
        return p;
    }

    // The verification window starts when the player joined the run (fallback: a year back if missing).
    private LocalDateTime windowFrom(ChallengeProgress progress) {
        return progress.getStartAt() != null ? progress.getStartAt() : LocalDateTime.now().minusYears(1);
    }

    // How many days the FITNESS verification window looks back, by the challenge's period.
    private long periodDays(Period period) {
        if (period == null) {
            return 7;
        }
        return switch (period) {
            case DAILY -> 1;
            case WEEKLY -> 7;
            case MONTHLY -> 31;
        };
    }

    // The player's Neotek PSU id (from their linked NEOTEK account), or the demo PSU id as a fallback.
    private String resolvePsuId(KingdomMembership membership) {
        if (membership != null && membership.getPlayer() != null) {
            ConnectedAccount account = connectedAccountRepository.findByPlayer_IdAndProvider(
                    membership.getPlayer().getId(), ConnectedProvider.NEOTEK);
            if (account != null && account.getExternalUserId() != null && !account.getExternalUserId().isBlank()) {
                return account.getExternalUserId();
            }
        }
        return demoPsuId;
    }

    // The player's own Strava refresh token (from their linked STRAVA account), or null to fall back to the
    // configured demo athlete. Lets each player be verified against THEIR OWN Strava activities.
    private String resolveStravaRefreshToken(KingdomMembership membership) {
        if (membership != null && membership.getPlayer() != null) {
            ConnectedAccount account = connectedAccountRepository.findByPlayer_IdAndProvider(
                    membership.getPlayer().getId(), ConnectedProvider.STRAVA);
            if (account != null && account.getRefreshToken() != null && !account.getRefreshToken().isBlank()) {
                return account.getRefreshToken();
            }
        }
        return null;
    }

    // Cancel (leave) an active challenge (sets it to CANCELED).
    public void cancelChallenge(Integer id) {
        ChallengeProgress progress = getChallengeProgressById(id);
        AuthUtil.requireSelfOrAdmin(progress.getKingdomMembership().getPlayer().getId());
        if (progress.getStatus() != ProgressStatus.JOINED && progress.getStatus() != ProgressStatus.IN_PROGRESS) {
            throw new ApiException("Only an active challenge can be canceled");
        }
        progress.setStatus(ProgressStatus.CANCELED);
        challengeProgressRepository.save(progress);
    }

    // A player's own challenge history across every kingdom they belong to.
    public List<ChallengeProgress> getProgressByPlayer(Integer playerId) {
        return challengeProgressRepository.findAllByKingdomMembership_Player_Id(playerId);
    }

    // The player's currently active challenges (JOINED/IN_PROGRESS).
    public List<ChallengeProgress> getActiveByPlayer(Integer playerId) {
        return challengeProgressRepository.findAllByKingdomMembership_Player_IdAndStatusIn(
                playerId, List.of(ProgressStatus.JOINED, ProgressStatus.IN_PROGRESS));
    }

    // A player's runs filtered to one status (e.g. VERIFIED = "my completed", or CANCELED).
    public List<ChallengeProgress> getProgressByPlayerAndStatus(Integer playerId, ProgressStatus status) {
        return challengeProgressRepository.findAllByKingdomMembership_Player_IdAndStatus(playerId, status);
    }

    // XP / daily streak / division live in ProgressRewardService (shared with the WhatsApp quiz finish path).

    //maysun endpoints
    public String submitGithubChallenge(Integer kingdomMembershipId, Integer challengeId, GithubSubmissionIn githubSubmissionIn) {
        // LOBBY NOTE (Maysun): to make a Programming lobby challenge auto-resolve (FIRST-to-verify wins), add,
        // right after the run is verified:  lobbyChallengeService.onChallengeVerified(progress);
        // and skip XP for lobby runs via lobbyChallengeService.isLobbyChallenge(challengeId, playerId).
        Challenge challenge = challengeRepository.findChallengeById(challengeId);
        if (challenge == null) {
            throw new ApiException("Challenge not found");
        }
        if (!"GITHUB".equalsIgnoreCase(challenge.getVerificationSource())) {
            throw new ApiException("This challenge is not verified via GitHub");
        }

        KingdomMembership membership = kingdomMembershipRepository.findKingdomMembershipById(kingdomMembershipId);
        if (membership == null) {
            throw new ApiException("Kingdom membership not found");
        }
        AuthUtil.requireSelfOrAdmin(membership.getPlayer().getId());
        if (challenge.getKingdom() == null || membership.getKingdom() == null
                || !membership.getKingdom().getId().equals(challenge.getKingdom().getId())) {
            throw new ApiException("This kingdom membership does not belong to the same kingdom as the challenge");
        }

        // Use the run created by joinChallenge (which already sent the join WhatsApp). Coding is now join -> submit,
        // like every other kingdom, instead of silently creating a fresh run here (which skipped the notification).
        ChallengeProgress progress =
                challengeProgressRepository.findByKingdomMembershipIdAndChallengeId(kingdomMembershipId, challengeId);
        if (progress == null) {
            throw new ApiException("انضم إلى التحدي أولاً ثم أرسل رابط مشروعك على GitHub.");
        }
        if (progress.getStatus() == ProgressStatus.VERIFIED) {
            throw new ApiException("لقد أكملت هذا التحدي بالفعل.");
        }

        boolean verified = githubService.verifyRepository(githubSubmissionIn.getRepoUrl());

        if (!verified) {
            // Keep the run active so the player can push a commit and submit again — no reject, no error.
            progress.setStatus(ProgressStatus.JOINED);
            challengeProgressRepository.save(progress);
            return "لم نجد commits بعد — التحدي ما زال قيد التنفيذ، ارفع commit وأرسل الرابط مرة أخرى!";
        }

        progress.setStatus(ProgressStatus.VERIFIED);
        progress.setFinishedAt(LocalDateTime.now());

        int currentXp = membership.getTotalXP() == null ? 0 : membership.getTotalXP();
        Integer reward = challenge.getXpReward();
        int xpAwarded = reward == null ? 0 : reward;

        progress.setVerifiedValue(xpAwarded);
        challengeProgressRepository.save(progress);

        if (challenge.getScope() != ChallengeScope.LOBBY) {
            membership.setTotalXP(currentXp + xpAwarded);
            kingdomMembershipRepository.save(membership);
        }
        return "تم التحقق من تحدي GitHub بنجاح — تم منحك نقاط الخبرة!";
    }

    public void submitKnowledgeChallenge(Integer kingdomMembershipId, Integer challengeId, Integer questionId, KnowledgeSubmissionIn knowledgeSubmissionIn) {

        Challenge challenge = challengeRepository.findChallengeById(challengeId);
        if (challenge == null) {
            throw new ApiException("Challenge not found");
        }

        if (!"AI_KNOWLEDGE".equalsIgnoreCase(challenge.getVerificationSource())) {
            throw new ApiException("This challenge is not a knowledge quiz challenge");
        }

        KingdomMembership membership = kingdomMembershipRepository.findKingdomMembershipById(kingdomMembershipId);
        if (membership == null) {
            throw new ApiException("Kingdom membership not found");
        }

        if (challenge.getKingdom() == null || membership.getKingdom() == null || !membership.getKingdom().getId().equals(challenge.getKingdom().getId())) {
            throw new ApiException("This kingdom membership does not belong to the same kingdom as the challenge");
        }

        ChallengeQuestion question = challengeQuestionRepository.findChallengeQuestionById(questionId);
        if (question == null) {
            throw new ApiException("Question not found");
        }

        if (question.getChallenge() == null || !question.getChallenge().getId().equals(challengeId)) {
            throw new ApiException("This question does not belong to this challenge");
        }

        checkAlreadyCompleted(kingdomMembershipId, challengeId);

        boolean correct = question.getCorrectAnswer()
                .equalsIgnoreCase(knowledgeSubmissionIn.getSelectedAnswer());

        ChallengeProgress progress = new ChallengeProgress();
        progress.setKingdomMembership(membership);
        progress.setChallenge(challenge);
        progress.setStartAt(LocalDateTime.now());
        progress.setFinishedAt(LocalDateTime.now());
        // Quiz = a graded attempt: a wrong answer is TERMINAL (REJECTED), not retryable. (Note: this HTTP
        // single-question path is currently unused.)
        progress.setStatus(correct ? ProgressStatus.VERIFIED : ProgressStatus.REJECTED);
        progress.setVerifiedValue(correct ? 1 : 0);

        challengeProgressRepository.save(progress);

        if (correct && challenge.getScope() != ChallengeScope.LOBBY) {
            int currentXp = membership.getTotalXP() == null ? 0 : membership.getTotalXP();
            int xpAwarded = challenge.getXpReward() == null ? 0 : challenge.getXpReward();

            membership.setTotalXP(currentXp + xpAwarded);
            kingdomMembershipRepository.save(membership);
        }

        if (!correct) {
            throw new ApiException("Wrong answer");
        }
    }
    public String submitNutritionImage(Integer progressId, MultipartFile image) {
        // Uses the run created by joinChallenge (join -> submit; no membership/challenge ids in the path). The
        // WhatsApp path (completeNutritionByPhone) finds the same JOINED run by phone.
        ChallengeProgress progress = getChallengeProgressById(progressId);
        Challenge challenge = progress.getChallenge();
        if (challenge == null || !"FOOD_IMAGE_ANALYSIS".equalsIgnoreCase(challenge.getVerificationRule())) {
            throw new ApiException("هذا التحدي ليس تحدي تغذية.");
        }
        KingdomMembership membership = progress.getKingdomMembership();
        AuthUtil.requireSelfOrAdmin(membership.getPlayer().getId());
        if (progress.getStatus() != ProgressStatus.JOINED) {
            throw new ApiException("انضم إلى تحدي التغذية أولاً ثم أرسل صورة وجبتك.");
        }

        byte[] imageBytes;
        try {
            imageBytes = image.getBytes();
        } catch (Exception e) {
            throw new ApiException("تعذّر قراءة الصورة المرفقة. حاول مرة أخرى.");
        }

        // Same AI analysis + message as the WhatsApp path, so HTTP and WhatsApp return identical macros + reason.
        return finishNutritionImage(progress, challenge, membership, imageBytes, image.getContentType());
    }

    private boolean steamCheck(ChallengeProgress progress) {
        Challenge challenge = progress.getChallenge();

        String steamId = connectedAccountService.getSteamId(progress.getKingdomMembership().getPlayer());

        System.out.println("=== STEAM CHECK ===");
        System.out.println("Steam ID: " + steamId);
        System.out.println("Game: " + challenge.getTargetName());
        System.out.println("Rule: " + challenge.getVerificationRule());
        System.out.println("Target: " + challenge.getVerificationTarget());
        System.out.println("Target Value: " + challenge.getTargetValue());
        System.out.println("===================");

        String rule = challenge.getVerificationRule();

        boolean verified = switch (rule) {

            case "PLAYTIME" ->
                    steamService.verifyPlaytime(
                            steamId,
                            challenge.getTargetName(),
                            challenge.getTargetValue()
                    );

            case "ACHIEVEMENT" ->
                    steamService.verifyAchievement(
                            steamId,
                            challenge.getTargetName(),
                            challenge.getVerificationTarget()
                    );

            case "ACHIEVEMENT_COUNT" ->
                    steamService.verifyAchievementCount(
                            steamId,
                            challenge.getTargetName(),
                            challenge.getTargetValue()
                    );

            default -> throw new ApiException("Unsupported verification rule");
        };

        return verified;
    }
    // Verification helpers

    private void githubCheck(ChallengeProgress progress, String repoUrl) {

        boolean verified = githubService.verifyRepository(repoUrl);

        if (!verified) {
            // Failed proof = retryable: keep the run JOINED so the player can push a commit and submit again
            // (consistent with submitGithubChallenge). No terminal reject. (Note: this helper is currently unused.)
            progress.setStatus(ProgressStatus.JOINED);
            challengeProgressRepository.save(progress);
            throw new ApiException("Repository exists but contains no commits");
        }
    }
    //helper method
    private Challenge checkChallengeForNutritionImage(Integer challengeId) {

        Challenge challenge = challengeRepository.findChallengeById(challengeId);

        if (challenge == null) {
            throw new ApiException("Challenge not found");
        }

        String src = challenge.getVerificationSource();
        if (!"NUTRITION".equalsIgnoreCase(src) && !"WHATSAPP".equalsIgnoreCase(src)) {
            throw new ApiException("هذا التحدي ليس تحدي تغذية.");
        }

        if (!"FOOD_IMAGE_ANALYSIS".equalsIgnoreCase(challenge.getVerificationRule())) {
            throw new ApiException("هذا التحدي يتطلب إرسال صورة وجبة.");
        }

        return challenge;
    }
    private KingdomMembership checkMembershipMatchesChallenge(Integer kingdomMembershipId, Challenge challenge) {

        KingdomMembership membership =
                kingdomMembershipRepository.findKingdomMembershipById(kingdomMembershipId);
        if (membership == null) {
            throw new ApiException("Kingdom membership not found");
        }

        if (!membership.getKingdom().getId()
                .equals(challenge.getKingdom().getId())) {

            throw new ApiException(
                    "This challenge does not belong to the player's kingdom");
        }

        return membership;
    }
    private void checkAlreadyCompleted(Integer kingdomMembershipId, Integer challengeId) {

        ChallengeProgress progress = challengeProgressRepository.findByKingdomMembershipIdAndChallengeId(kingdomMembershipId, challengeId);

        if (progress != null && progress.getStatus() == ProgressStatus.VERIFIED) {
            throw new ApiException("Challenge already completed");
        }
    }

    // ===== Maysun's WhatsApp nutrition + knowledge handlers (grafted from her branch; used by WhatsAppWebhookService) =====
    public String submitNutritionImageFromWhatsApp(Integer progressId, byte[] imageBytes, String contentType) {
        ChallengeProgress progress = getChallengeProgressById(progressId);
        Challenge challenge = progress.getChallenge();

        String src = challenge.getVerificationSource();
        if (!"WHATSAPP".equalsIgnoreCase(src) && !"NUTRITION".equalsIgnoreCase(src)) {
            throw new ApiException("This challenge must be submitted via WhatsApp");
        }
        if (!"FOOD_IMAGE_ANALYSIS".equalsIgnoreCase(challenge.getVerificationRule())) {
            throw new ApiException("This challenge requires food image analysis");
        }
        if (progress.getStatus() != ProgressStatus.JOINED) {
            throw new ApiException("This challenge has already been completed");
        }

        return finishNutritionImage(progress, challenge, progress.getKingdomMembership(), imageBytes, contentType);
    }

    // Shared nutrition-image judgement used by BOTH the HTTP submit-image endpoint and the WhatsApp handler, so they
    // return identical output (real estimated macros + an accept/reject reason). A failed photo keeps the run JOINED
    // (retryable); a passing photo verifies it and awards XP.
    private String finishNutritionImage(ChallengeProgress progress, Challenge challenge, KingdomMembership membership,
                                        byte[] imageBytes, String contentType) {
        String aiAnalysis = analyzeNutritionImageBytes(imageBytes, contentType, challenge);
        boolean accepted = aiAnalysis.contains("\"challengePassed\": true");
        String cleanAnalysis = cleanAiAnalysis(aiAnalysis);

        if (!accepted) {
            // Failed proof = retryable: keep the run JOINED (no reject, no finishedAt) so the player can send
            // another photo.
            progress.setStatus(ProgressStatus.JOINED);
            progress.setRejectionReason(null);
            challengeProgressRepository.save(progress);
            return """
                ❌ لم يتم قبول صورة الوجبة

                🍽️ التحليل التقديري:
                %s

                حاول مرة أخرى بصورة أوضح ومطابقة للتحدي.
                """.formatted(cleanAnalysis);
        }

        int xpAwarded = challenge.getXpReward() == null ? 0 : challenge.getXpReward();
        progress.setFinishedAt(LocalDateTime.now());
        progress.setStatus(ProgressStatus.VERIFIED);
        progress.setVerifiedValue(xpAwarded);
        challengeProgressRepository.save(progress);

        if (challenge.getScope() != ChallengeScope.LOBBY) {
            int currentXp = membership.getTotalXP() == null ? 0 : membership.getTotalXP();
            membership.setTotalXP(currentXp + xpAwarded);
            kingdomMembershipRepository.save(membership);
        }

        return """
            ✅ تم قبول صورة الوجبة!

            🍽️ التحليل التقديري:
            %s

            👑 النتيجة:
            تم إكمال التحدي بنجاح

            🎉 المكافأة:
            كسبت %s نقطة XP

            ملاحظة: التحليل والقيم الغذائية تقديرية وليست نصيحة طبية.
            """.formatted(cleanAnalysis, xpAwarded);
    }

    public boolean submitKnowledgeAnswerFromWhatsApp(Integer progressId, Integer questionId, String selectedAnswer) {
        KnowledgeAnswerIn answerIn = new KnowledgeAnswerIn();
        answerIn.setQuestionId(questionId);
        answerIn.setSelectedAnswer(selectedAnswer);
        return submitKnowledgeAnswer(progressId, answerIn);
    }

    public boolean submitKnowledgeAnswer(Integer progressId, KnowledgeAnswerIn answerIn) {
        ChallengeProgress progress = getChallengeProgressById(progressId);
        Challenge challenge = progress.getChallenge();

        if (!"WHATSAPP".equalsIgnoreCase(challenge.getVerificationSource())) {
            throw new ApiException("This challenge is not a WhatsApp question-based challenge");
        }
        if (progress.getStatus() != ProgressStatus.JOINED) {
            throw new ApiException("This challenge has already been completed");
        }

        ChallengeQuestion question = challengeQuestionRepository.findChallengeQuestionById(answerIn.getQuestionId());
        if (question == null) {
            throw new ApiException("Question not found");
        }
        if (question.getChallenge() == null || !question.getChallenge().getId().equals(challenge.getId())) {
            throw new ApiException("This question does not belong to this challenge");
        }

        boolean correct = question.getCorrectAnswer().equalsIgnoreCase(answerIn.getSelectedAnswer());
        if (correct) {
            int currentScore = progress.getVerifiedValue() == null ? 0 : progress.getVerifiedValue();
            progress.setVerifiedValue(currentScore + 1);
            challengeProgressRepository.save(progress);
        }
        return correct;
    }

    public void finishKnowledgeChallenge(Integer progressId) {
        ChallengeProgress progress = getChallengeProgressById(progressId);
        Challenge challenge = progress.getChallenge();

        if (!"WHATSAPP".equalsIgnoreCase(challenge.getVerificationSource())) {
            throw new ApiException("This challenge is not a WhatsApp question-based challenge");
        }
        if (progress.getStatus() != ProgressStatus.JOINED) {
            throw new ApiException("This challenge has already been completed");
        }

        int correctCount = progress.getVerifiedValue() == null ? 0 : progress.getVerifiedValue();
        boolean passed = correctCount >= 4; // pass bar: at least 4 of 5 correct (80%)
        progress.setFinishedAt(LocalDateTime.now());

        if (!passed) {
            // Quiz = a graded attempt: failing is TERMINAL (REJECTED), not retryable — unlike upload/auto-verify
            // proof (fitness / PDF / food image / steam) which stays JOINED so the player can try again.
            progress.setStatus(ProgressStatus.REJECTED);
            progress.setRejectionReason(RejectionReason.KNOWLEDGE_SCORE_TOO_LOW);
            challengeProgressRepository.save(progress);
            throw new ApiException("لم تجتز الاختبار — أجبت صحيحًا على " + correctCount + " من 5 (المطلوب 4 على الأقل).");
        }

        progress.setStatus(ProgressStatus.VERIFIED);
        challengeProgressRepository.save(progress);

        if (challenge.getScope() != ChallengeScope.LOBBY) {
            KingdomMembership membership = progress.getKingdomMembership();
            int currentXp = membership.getTotalXP() == null ? 0 : membership.getTotalXP();
            int xpAwarded = challenge.getXpReward() == null ? 0 : challenge.getXpReward();
            membership.setTotalXP(currentXp + xpAwarded);
            kingdomMembershipRepository.save(membership);
        }
    }

    private String analyzeNutritionImageBytes(byte[] imageBytes, String mimeType, Challenge challenge) {
        try {
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "image/jpeg";
            }
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            Map<String, Object> body = Map.of(
                    "model", "gpt-4.1-mini",
                    "input", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", List.of(
                                            Map.of(
                                                    "type", "input_text",
                                                    "text", """
                                                        حلل صورة الوجبة وتأكد هل تطابق التحدي المطلوب.

                                                        بيانات التحدي:
                                                        العنوان: %s
                                                        الوصف: %s
                                                        الهدف: %s

                                                        أرجع JSON فقط (بدون أي نص خارج JSON) بهذا الشكل بالضبط:
                                                        {
                                                          "challengePassed": true,
                                                          "carbs": "تقدير الكربوهيدرات بالغرام",
                                                          "protein": "تقدير البروتين بالغرام",
                                                          "fat": "تقدير الدهون بالغرام",
                                                          "estimatedCalories": "تقدير السعرات الحرارية",
                                                          "summary": "اسم الوجبة بإيجاز ثم سبب القبول أو الرفض بجملة عربية واضحة"
                                                        }

                                                        قواعد:
                                                        - إذا لم تكن الصورة طعاماً، أو كانت وجبة غير صحية بينما التحدي يطلب وجبة صحية، اجعل challengePassed=false واشرح السبب في summary.
                                                        - قدّر carbs و protein و fat و estimatedCalories دائماً (تقديري) حتى لو رُفضت الوجبة، واكتب كل القيم بالعربية.
                                                        """.formatted(
                                                            challenge.getTitle(),
                                                            challenge.getDescription(),
                                                            challenge.getTargetName()
                                                    )
                                            ),
                                            Map.of(
                                                    "type", "input_image",
                                                    "image_url", "data:" + mimeType + ";base64," + base64Image,
                                                    "detail", "low"
                                            )
                                    )
                            )
                    )
            );

            String response = openAiWebClient.post()
                    .uri("/responses")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            return root.path("output").get(0).path("content").get(0).path("text").asText();
        } catch (Exception e) {
            System.out.println("Nutrition image analysis failed (WhatsApp path): " + e.getMessage());
            throw new ApiException("تعذّر تحليل صورة الوجبة حالياً. حاول مرة أخرى بعد قليل بصورة أوضح.");
        }
    }

    private String cleanAiAnalysis(String aiAnalysis) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(aiAnalysis);
            String carbs = root.path("carbs").asText("غير معروف");
            String protein = root.path("protein").asText("غير معروف");
            String fat = root.path("fat").asText("غير معروف");
            String calories = root.path("estimatedCalories").asText("غير معروف");
            String summary = root.path("summary").asText("");
            return """
                🥖 الكاربوهيدرات: %s
                🍗 البروتين: %s
                🥑 الدهون: %s
                🔥 السعرات التقديرية: %s

                💬 %s
                """.formatted(carbs, protein, fat, calories, summary);
        } catch (Exception e) {
            return aiAnalysis;
        }
    }
    }
