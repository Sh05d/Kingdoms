package com.kingdom.Service.ForTestImage;

import com.kingdom.Service.ChallengeProgressService;
import com.kingdom.Service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.kingdom.Model.ChallengeProgress;
import com.kingdom.Model.ChallengeQuestion;
import com.kingdom.Repository.ChallengeQuestionRepository;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WhatsAppWebhookService {

    private final ChallengeProgressService challengeProgressService;
    private final ChallengeQuestionRepository challengeQuestionRepository;
    private final WhatsAppService whatsAppService;

    private final Map<String, Integer> activeQuizProgressByPhone = new HashMap<>();
    private final Map<String, Integer> currentQuestionIndexByPhone = new HashMap<>();

    @Async
    public void handleIncomingNutritionImageAsync(String from, String body, String numMedia, String mediaUrl, String contentType) {
        try {
            System.out.println("===== WHATSAPP WEBHOOK START =====");
            System.out.println("FROM = " + from);
            System.out.println("BODY = " + body);
            System.out.println("NUM MEDIA = " + numMedia);
            System.out.println("MEDIA URL = " + mediaUrl);
            System.out.println("CONTENT TYPE = " + contentType);

            if (body != null && body.trim().toLowerCase().startsWith("quiz")) {
                handleQuizStart(from, body);
                return;
            }

            if (activeQuizProgressByPhone.containsKey(from)) {
                handleQuizAnswer(from, body);
                return;
            }

            if (numMedia != null && !numMedia.equals("0") && mediaUrl != null && !mediaUrl.isBlank()) {
                handleNutritionImage(from, body, mediaUrl, contentType);
                return;
            }

            whatsAppService.sendMessage(from, """
                    أرسلي:
                    - صورة الوجبة مع رقم progressId في الكابتشن
                    أو
                    - quiz 3 لبدء كويز التحدي
                    """);

        } catch (Exception e) {
            e.printStackTrace();
            whatsAppService.sendMessage(from, "صار خطأ في معالجة رسالتك: " + e.getMessage());
        }
    }

    private void handleNutritionImage(String from, String body, String mediaUrl, String contentType) {
        try {
            if (body == null || body.isBlank()) {
                whatsAppService.sendMessage(from, "اكتبي رقم progressId في كابتشن الصورة، مثال: 3");
                return;
            }

            Integer progressId = Integer.parseInt(body.trim());

            byte[] imageBytes = whatsAppService.downloadMedia(mediaUrl);

            if (imageBytes == null || imageBytes.length == 0) {
                whatsAppService.sendMessage(from, "ما قدرت أحمل الصورة من واتساب. جربي ترسلينها مرة ثانية.");
                return;
            }

            System.out.println("IMAGE BYTES = " + imageBytes.length);
            System.out.println("===== START AI ANALYSIS =====");

            String resultMessage = challengeProgressService.submitNutritionImageFromWhatsApp(progressId, imageBytes, contentType);

            System.out.println("===== AI ANALYSIS RESULT =====");
            System.out.println(resultMessage);

            boolean sent = whatsAppService.sendMessage(from, resultMessage);
            System.out.println("FINAL WHATSAPP RESULT SENT = " + sent);

        } catch (NumberFormatException e) {
            whatsAppService.sendMessage(from, "رقم progressId غير صحيح. اكتبي الرقم فقط في كابتشن الصورة، مثال: 3");
        } catch (Exception e) {
            e.printStackTrace();
            whatsAppService.sendMessage(from, "صار خطأ أثناء تحليل الصورة: " + e.getMessage());
        }
    }

    private void handleQuizStart(String from, String body) {
        try {
            String[] parts = body.trim().split("\\s+");

            if (parts.length < 2) {
                whatsAppService.sendMessage(from, "لبدء الكويز اكتبي: quiz progressId مثال: quiz 3");
                return;
            }

            Integer progressId = Integer.parseInt(parts[1]);

            ChallengeProgress progress = challengeProgressService.getChallengeProgressById(progressId);

            if (!"WHATSAPP".equalsIgnoreCase(progress.getChallenge().getVerificationSource())) {
                whatsAppService.sendMessage(from, "هذا التحدي ليس مخصصًا للواتساب.");
                return;
            }

            if (!"ANSWER_QUESTIONS".equalsIgnoreCase(progress.getChallenge().getVerificationRule())) {
                whatsAppService.sendMessage(from, "هذا التحدي ليس كويز أسئلة.");
                return;
            }

            List<ChallengeQuestion> questions =
                    challengeQuestionRepository.findAllByChallengeId(progress.getChallenge().getId());

            if (questions == null || questions.isEmpty()) {
                whatsAppService.sendMessage(from, "ما فيه أسئلة لهذا التحدي.");
                return;
            }

            activeQuizProgressByPhone.put(from, progressId);
            currentQuestionIndexByPhone.put(from, 0);

            sendCurrentQuestion(from);

        } catch (NumberFormatException e) {
            whatsAppService.sendMessage(from, "رقم progressId غير صحيح. مثال صحيح: quiz 3");
        } catch (Exception e) {
            e.printStackTrace();
            whatsAppService.sendMessage(from, "ما قدرت أبدأ الكويز: " + e.getMessage());
        }
    }

    private void handleQuizAnswer(String from, String body) {
        try {
            if (body == null || body.isBlank()) {
                whatsAppService.sendMessage(from, "اكتبي إجابتك مثل: A أو B أو C أو D");
                return;
            }

            Integer progressId = activeQuizProgressByPhone.get(from);
            Integer questionIndex = currentQuestionIndexByPhone.get(from);

            ChallengeProgress progress = challengeProgressService.getChallengeProgressById(progressId);

            List<ChallengeQuestion> questions = challengeQuestionRepository.findAllByChallengeId(progress.getChallenge().getId());

            if (questionIndex == null || questionIndex >= questions.size()) {
                finishQuiz(from, progressId);
                return;
            }

            ChallengeQuestion currentQuestion = questions.get(questionIndex);

            boolean correct = challengeProgressService.submitKnowledgeAnswerFromWhatsApp(progressId, currentQuestion.getId(), body.trim());

            currentQuestionIndexByPhone.put(from, questionIndex + 1);

            if (currentQuestionIndexByPhone.get(from) >= questions.size()) {
                finishQuiz(from, progressId);
                return;
            }

            String feedback = correct ? "✅ إجابة صحيحة!" : "❌ إجابة غير صحيحة.";
            whatsAppService.sendMessage(from, feedback + "\n\nالسؤال التالي:");
            sendCurrentQuestion(from);

        } catch (Exception e) {
            e.printStackTrace();
            whatsAppService.sendMessage(from, "صار خطأ أثناء تسجيل الإجابة: " + e.getMessage());
        }
    }

    private void sendCurrentQuestion(String from) {
        Integer progressId = activeQuizProgressByPhone.get(from);
        Integer questionIndex = currentQuestionIndexByPhone.get(from);

        ChallengeProgress progress = challengeProgressService.getChallengeProgressById(progressId);

        List<ChallengeQuestion> questions =
                challengeQuestionRepository.findAllByChallengeId(progress.getChallenge().getId());

        ChallengeQuestion question = questions.get(questionIndex);

        String message = """
                🧠 سؤال %s من %s

                %s

                A) %s
                B) %s
                C) %s
                D) %s

                ارسلي الإجابة بحرف فقط: A أو B أو C أو D
                """.formatted(
                questionIndex + 1,
                questions.size(), question.getQuestion(), question.getOptionA(), question.getOptionB(), question.getOptionC(), question.getOptionD());

        whatsAppService.sendMessage(from, message);
    }

    private void finishQuiz(String from, Integer progressId) {
        try {
            challengeProgressService.finishKnowledgeChallenge(progressId);

            activeQuizProgressByPhone.remove(from);
            currentQuestionIndexByPhone.remove(from);

            ChallengeProgress progress = challengeProgressService.getChallengeProgressById(progressId);
            Integer score = progress.getVerifiedValue();

            whatsAppService.sendMessage(from, """
                    🎉 انتهى الكويز!

                    نتيجتك: %s إجابات صحيحة

                    ✅ تم إكمال التحدي بنجاح
                    """.formatted(score == null ? 0 : score));

        } catch (Exception e) {
            activeQuizProgressByPhone.remove(from);
            currentQuestionIndexByPhone.remove(from);

            whatsAppService.sendMessage(from, "انتهى الكويز، لكن لم يتم اجتياز التحدي: " + e.getMessage());
        }
    }
}