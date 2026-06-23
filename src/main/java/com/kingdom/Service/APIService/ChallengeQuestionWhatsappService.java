package com.kingdom.Service.APIService;
import com.kingdom.API.ApiException;
import com.kingdom.Model.ChallengeQuestion;
import com.kingdom.Repository.ChallengeQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Enums.RejectionReason;
import com.kingdom.Model.ChallengeProgress;
import com.kingdom.Repository.ChallengeProgressRepository;
import com.kingdom.Repository.KingdomMembershipRepository;
import com.kingdom.Service.LobbyChallengeService;
import com.kingdom.Service.ProgressRewardService;
import com.kingdom.Service.WhatsAppService;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChallengeQuestionWhatsappService {
    private final ChallengeQuestionRepository challengeQuestionRepository;
    private final ChallengeProgressRepository challengeProgressRepository;
    private final KingdomMembershipRepository kingdomMembershipRepository;
    private final WhatsAppService whatsAppService;
    // Anas owns XP/streak/division; the quiz finish routes through the shared service so all three update.
    private final ProgressRewardService progressRewardService;
    // Auto-resolve a lobby if this quiz IS a lobby challenge (first finisher wins).
    private final LobbyChallengeService lobbyChallengeService;

    // Twilio List Picker template (HX...) that presents each question as 4 tappable rows (id A/B/C/D).
    // When blank, we fall back to plain text (the player replies with a letter). Both paths grade the same.
    @Value("${twilio.content.quiz-sid:}")
    private String quizContentSid;

    @Transactional
    public void sendChallengeQuestions(Integer challengeProgressId) {

        ChallengeProgress progress =
                challengeProgressRepository.findChallengeProgressById(challengeProgressId);

        if (progress == null) {
            throw new ApiException("Challenge progress not found");
        }

        if (progress.getStatus() != ProgressStatus.JOINED) {
            throw new ApiException("Only joined challenge can start WhatsApp questions");
        }

        String phone = progress.getKingdomMembership().getPlayer().getUser().getPhoneNumber();

        List<ChallengeQuestion> questions =
                challengeQuestionRepository.findQuestionsByChallengeId(
                        progress.getChallenge().getId()
                );

        if (questions.isEmpty()) {
            throw new ApiException("No questions found for this challenge");
        }

        progress.setStatus(ProgressStatus.IN_PROGRESS);
        progress.setCurrentQuestionIndex(0);
        progress.setVerifiedValue(0);
        progress.setFinishedAt(null);
        progress.setRejectionReason(null);

        challengeProgressRepository.save(progress);

        boolean introSent = whatsAppService.sendMessage(
                phone,
                buildIntroMessage(progress.getChallenge().getTitle())
        );

        if (!introSent) {
            throw new ApiException("Failed to send WhatsApp intro message");
        }

        deliverQuestion(phone, questions.get(0), 1, questions.size());
    }

    @Transactional
    public String handleIncomingAnswer(String phone, String messageBody) {

        String normalizedPhone = normalizePhone(phone);

        List<ChallengeProgress> activeProgresses =
                challengeProgressRepository.findActiveProgressByPhone(
                        normalizedPhone,
                        ProgressStatus.IN_PROGRESS
                );

        if (activeProgresses.isEmpty()) {
            whatsAppService.sendMessage(normalizedPhone, "لا يوجد اختبار نشط لهذا الرقم. ابدأ التحدي أولًا 👑");
            return "no active quiz";
        }

        ChallengeProgress progress = activeProgresses.get(0);

        List<ChallengeQuestion> questions =
                challengeQuestionRepository.findQuestionsByChallengeId(
                        progress.getChallenge().getId()
                );

        if (questions.isEmpty()) {
            progress.setStatus(ProgressStatus.REJECTED);
            progress.setRejectionReason(RejectionReason.NOT_COMPLETED);
            progress.setFinishedAt(LocalDateTime.now());
            challengeProgressRepository.save(progress);

            whatsAppService.sendMessage(normalizedPhone, "لا توجد أسئلة لهذا التحدي.");
            return "no questions";
        }

        Integer currentIndex = progress.getCurrentQuestionIndex();
        if (currentIndex == null) {
            currentIndex = 0;
        }

        if (currentIndex >= questions.size()) {
            boolean isLobby = finishWhatsappChallenge(progress, questions.size());
            whatsAppService.sendMessage(normalizedPhone,
                    buildFinalMessage(progress.getVerifiedValue(), questions.size(), progress.getStatus(), progress.getChallenge().getXpReward(), isLobby));
            return "already finished";
        }

        ChallengeQuestion currentQuestion = questions.get(currentIndex);

        // Accept either a typed letter (أ/ب/ج/د/A-D) or a tapped List Picker row (its title is the letter,
        // or the tapped text equals one of the option texts).
        String answer = resolveAnswer(messageBody, currentQuestion);

        if (answer == null) {
            whatsAppService.sendMessage(normalizedPhone, "الرجاء اختيار أحد الخيارات: أ أو ب أو ج أو د");
            return "invalid answer";
        }

        String correctAnswer = normalizeAnswer(currentQuestion.getCorrectAnswer());

        if (answer.equals(correctAnswer)) {
            Integer oldScore = progress.getVerifiedValue() == null ? 0 : progress.getVerifiedValue();
            progress.setVerifiedValue(oldScore + 1);
        }

        int nextIndex = currentIndex + 1;
        progress.setCurrentQuestionIndex(nextIndex);

        if (nextIndex >= questions.size()) {
            boolean isLobby = finishWhatsappChallenge(progress, questions.size());
            whatsAppService.sendMessage(normalizedPhone,
                    buildFinalMessage(progress.getVerifiedValue(), questions.size(), progress.getStatus(), progress.getChallenge().getXpReward(), isLobby));
            return "finished";
        }

        challengeProgressRepository.save(progress);
        deliverQuestion(normalizedPhone, questions.get(nextIndex), nextIndex + 1, questions.size());
        return "next question sent";
    }


    private boolean finishWhatsappChallenge(ChallengeProgress progress, int totalQuestions) {

        // Lobby challenges are competition-only — NO XP. Only solo (kingdom) quizzes award XP.
        boolean isLobby = progress.getChallenge() != null && progress.getKingdomMembership() != null
                && progress.getKingdomMembership().getPlayer() != null
                && lobbyChallengeService.isLobbyChallenge(
                        progress.getChallenge().getId(), progress.getKingdomMembership().getPlayer().getId());

        if (isLobby) {
            // Lobby competition: NO pass/fail and NO XP. The player "completed" the quiz; the lobby ranks by
            // score (most correct, ties -> earliest finish). Mark VERIFIED so the resolver counts this finish.
            progress.setStatus(ProgressStatus.VERIFIED);
            progress.setRejectionReason(null);
        } else {
            Integer score = progress.getVerifiedValue() == null ? 0 : progress.getVerifiedValue();
            double percentage = ((double) score / totalQuestions) * 100;
            if (percentage >= 80) { // pass bar: at least 4 of 5 correct (80%)
                // Award XP + daily streak + division via the shared owner BEFORE marking VERIFIED, so the
                // streak query doesn't count the run that is finishing right now as "already finished today".
                progressRewardService.applyVerifiedReward(progress, progress.getKingdomMembership());
                progress.setStatus(ProgressStatus.VERIFIED);
                progress.setRejectionReason(null);
            } else {
                // Quiz = a graded attempt: failing is TERMINAL (REJECTED), not retryable — unlike upload/
                // auto-verify proof which stays JOINED so the player can try again.
                progress.setStatus(ProgressStatus.REJECTED);
                progress.setRejectionReason(RejectionReason.KNOWLEDGE_SCORE_TOO_LOW);
            }
        }

        if (progress.getStatus() == ProgressStatus.VERIFIED || progress.getStatus() == ProgressStatus.REJECTED) {
            progress.setFinishedAt(LocalDateTime.now());
        }
        challengeProgressRepository.save(progress);

        // Lobby challenge -> hand off to the resolver (rank by score / wait for the rest / crown the winner).
        if (progress.getStatus() == ProgressStatus.VERIFIED) {
            lobbyChallengeService.onChallengeVerified(progress);
        }
        return isLobby;
    }

    // Send one question. With a List Picker template configured -> tappable rows (id A/B/C/D); the question +
    // progress go in the body {{1}}, the 4 options in {{2}}..{{5}}. Otherwise -> plain text (reply a letter).
    private void deliverQuestion(String phone, ChallengeQuestion question, int number, int total) {
        if (notBlank(quizContentSid)) {
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("1", "سؤال " + number + " من " + total + ":\n\n" + question.getQuestion());
            vars.put("2", question.getOptionA());
            vars.put("3", question.getOptionB());
            vars.put("4", question.getOptionC());
            vars.put("5", question.getOptionD());
            if (whatsAppService.sendContentTemplate(phone, quizContentSid, vars)) {
                return;
            }
            // template send failed -> fall back to plain text so the quiz still works
        }
        whatsAppService.sendMessage(phone, buildQuestionMessage(question, number, total));
    }

    // Map the player's reply to A/B/C/D: a typed/tapped letter, or the tapped row text matching an option.
    private String resolveAnswer(String body, ChallengeQuestion q) {
        String letter = normalizeAnswer(body);
        if (letter != null) {
            return letter;
        }
        if (body == null) {
            return null;
        }
        String t = body.trim();
        if (t.equalsIgnoreCase(safe(q.getOptionA()))) return "A";
        if (t.equalsIgnoreCase(safe(q.getOptionB()))) return "B";
        if (t.equalsIgnoreCase(safe(q.getOptionC()))) return "C";
        if (t.equalsIgnoreCase(safe(q.getOptionD()))) return "D";
        return null;
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String buildIntroMessage(String challengeTitle) {

        return """
                مرحبًا 👑
                هذه أسئلة تحدي:

                %s

                سيتم إرسال كل سؤال بعد إجابتك على السؤال السابق.
                """.formatted(challengeTitle);
    }

    private String buildQuestionMessage(
            ChallengeQuestion question,
            int questionNumber,
            int totalQuestions
    ) {

        return """
                سؤال %d من %d:

                %s

                أ) %s

                ب) %s

                ج) %s

                د) %s

                أرسل الإجابة بحرف واحد فقط:
                أ  أو  ب  أو  ج  أو  د
                """.formatted(
                questionNumber,
                totalQuestions,
                question.getQuestion(),
                question.getOptionA(),
                question.getOptionB(),
                question.getOptionC(),
                question.getOptionD()
        );
    }

    private String buildFinalMessage(Integer score, Integer totalQuestions, ProgressStatus status,
                                     Integer xpEarned, boolean isLobby) {

        // Lobby quiz: no pass/fail, no XP — just the score. The win/loss comes from the lobby resolver.
        if (isLobby) {
            return """
                    انتهى الاختبار 🏁

                    نتيجتك: %d من %d
                    ⚔️ تحدي لوبي تنافسي — لا نقاط خبرة، الفوز لأعلى نتيجة!
                    """.formatted(score, totalQuestions);
        }

        String result = status == ProgressStatus.VERIFIED
                ? "✅ تم اجتياز التحدي"
                : "❌ لم يتم اجتياز التحدي";

        String xpLine = (status == ProgressStatus.VERIFIED && xpEarned != null && xpEarned > 0)
                ? "\n🎯 ربحت " + xpEarned + " نقطة خبرة (XP)"
                : "";

        return """
                انتهى الاختبار 🏁

                نتيجتك: %d من %d
                %s%s

                شكرًا لمشاركتك في التحدي 👑
                """.formatted(score, totalQuestions, result, xpLine);
    }

    private String normalizeAnswer(String body) {

        if (body == null || body.isBlank()) {
            return null;
        }

        String value = body
                .trim()
                .replace(".", "")
                .replace(")", "")
                .replace("(", "")
                .replace("-", "")
                .replace("ـ", "")
                .toUpperCase();

        return switch (value) {
            case "A", "أ", "ا", "إ", "آ" -> "A";
            case "B", "ب" -> "B";
            case "C", "ج" -> "C";
            case "D", "د" -> "D";
            default -> null;
        };
    }

    private String normalizePhone(String phone) {

        String p = phone == null ? "" : phone.trim();

        if (p.toLowerCase().startsWith("whatsapp:")) {
            p = p.substring("whatsapp:".length()).trim();
        }

        if (p.startsWith("05")) {
            return "+966" + p.substring(1);
        }

        if (p.startsWith("5")) {
            return "+966" + p;
        }

        if (p.startsWith("966")) {
            return "+" + p;
        }

        return p.startsWith("+") ? p : "+" + p;
    }
}
