package com.kingdom.Service.AiService;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.KingdomType;
import com.kingdom.Enums.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Knowledge kingdom: AI-generates a 5-question multiple-choice quiz in ONE general-knowledge category.
 * Same idea as {@link FaithAiService} — the AI returns title/description + a "questions" array of exactly 5
 * MCQs. The challenge is then verified as a WhatsApp quiz (addChallenge forces verificationSource=WHATSAPP
 * and saves the 5 questions), so the finish flow sends the questions over WhatsApp and grades the answers.
 * Unlike Faith there is no content service: general knowledge comes from the model itself.
 */
@Service
@RequiredArgsConstructor
public class KnowledgeAiService implements KingdomAiService {

    private final OpenAiClient openAiClient;

    @Override
    public KingdomType kingdom() {
        return KingdomType.KNOWLEDGE;
    }

    @Override
    public String generateChallenge(Difficulty difficulty, Period period, List<String> existingChallenges) {

        String instructions = """
                أنت مسؤول عن توليد تحديات لمملكة المعرفة في منصة Kingdom.

                أرجع JSON صحيح فقط.
                لا تستخدم Markdown.
                لا تضف أي شرح خارج JSON.
                لا تضف تعليقات داخل JSON.

                القواعد العامة:
                - التحدي عبارة عن اختبار (Quiz) من 5 أسئلة في مجال معرفي واحد فقط.
                - اختر مجالاً واحداً فقط من المجالات التالية:
                  * التاريخ
                  * الجغرافيا
                  * العلوم
                  * الفضاء
                  * الأدب
                  * الشخصيات المشهورة
                  * الاختراعات
                  * اللغات
                  * الثقافة العامة
                - نوّع المجالات بين التحديات ولا تكرر نفس الفكرة أو العنوان.
                - title و description باللغة العربية.
                - اجعل title قصيراً وجذاباً ويشير إلى المجال المختار.
                - description يشرح باختصار أن المستخدم سيجيب عن 5 أسئلة في هذا المجال.

                قواعد الصعوبة:
                - EASY: معلومات عامة بسيطة يعرفها أغلب الناس.
                - MEDIUM: أسئلة تحتاج معرفة أوسع وربطاً وتحليلاً.
                - HARD: أسئلة متقدمة أو متخصصة داخل المجال المختار.

                قواعد الأسئلة:
                - أنشئ 5 أسئلة اختيار من متعدد بالضبط، لا أقل ولا أكثر.
                - كل سؤال يحتوي 4 خيارات: optionA, optionB, optionC, optionD.
                - correctAnswer يجب أن يكون فقط A أو B أو C أو D.
                - وزّع correctAnswer بين A و B و C و D، ولا تجعل الإجابة الصحيحة دائماً A.
                - يجب أن تكون المعلومة في كل سؤال صحيحة ودقيقة، ولا تخترع حقائق.
                - اجعل الخيارات الأربعة معقولة ومن نفس المجال، ولا تجعل الخيارات الخاطئة واضحة جداً أو مضحكة.
                - لا تستخدم عبارات مثل "كل ما سبق" أو "لا شيء مما سبق".
                - لا تكرر نفس فكرة السؤال أكثر من مرة.
                - اكتب الأسئلة بلغة عربية واضحة تناسب مستخدماً عادياً.
                """;

        String input = """
                الصعوبة: %s
                الفترة: %s

                عناوين تحديات مستخدمة مسبقاً ويجب تجنب تكرارها:
                %s

                اختر مجالاً واحداً، ثم أرجع JSON فقط بهذا الشكل:

                {
                  "title": "string",
                  "description": "string",
                  "targetName": "Knowledge Quiz",
                  "targetValue": 5,
                  "verificationSource": "WHATSAPP",
                  "verificationRule": "KNOWLEDGE_QUESTIONS",
                  "verificationTarget": "KNOWLEDGE_QUIZ",
                  "questions": [
                    {
                      "question": "string",
                      "optionA": "string",
                      "optionB": "string",
                      "optionC": "string",
                      "optionD": "string",
                      "correctAnswer": "A"
                    }
                  ]
                }

                شروط مهمة جداً:
                - questions يجب أن تحتوي بالضبط على 5 أسئلة، لا أقل ولا أكثر.
                - targetName يجب أن يكون دائماً "Knowledge Quiz".
                - targetValue يجب أن يكون دائماً 5.
                - verificationSource يجب أن يكون "WHATSAPP".
                - verificationRule يجب أن يكون "KNOWLEDGE_QUESTIONS".
                - verificationTarget يجب أن يكون "KNOWLEDGE_QUIZ".
                - correctAnswer يجب أن يكون فقط A أو B أو C أو D.
                - وزّع الإجابات الصحيحة بين A و B و C و D ولا تجعلها دائماً A.
                """.formatted(difficulty, period, existingChallenges);

        return openAiClient.generate(instructions, input);
    }
}
