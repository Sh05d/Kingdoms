package com.kingdom.Service.AiService;

import com.kingdom.DTO.OUT.IslamicContentDTO;
import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.KingdomType;
import com.kingdom.Enums.Period;
import com.kingdom.Service.APIService.FaithContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FaithAiService implements com.kingdom.Service.AiService.KingdomAiService {
    private final com.kingdom.Service.AiService.OpenAiClient openAiClient;
    private final FaithContentService faithContentService;

    @Override
    public KingdomType kingdom() {
        return KingdomType.FAITH;
    }

    @Override
    public String generateChallenge(Difficulty difficulty, Period period, List<String> existingChallenges) {
        // Pick one real, non-repeated surah (text + tafsir) from AlQuran Cloud + the tafseer API.
        List<IslamicContentDTO> contents =
                faithContentService.findSuitableIslamicContents(difficulty, period, existingChallenges);

        String instructions = """
            أنت مسؤول عن توليد تحديات لمملكة الإيمان في منصة Kingdom.

            أرجع JSON صحيح فقط.
            لا تستخدم Markdown.
            لا تضف أي شرح خارج JSON.

            القواعد العامة:
            - أمامك سورة واحدة من القرآن الكريم مع نصها وملخص تفسيرها.
            - التحدي هو قراءة السورة وتدبّر معانيها ثم الإجابة عن اختبار قصير.
            - اعتمد فقط على نص السورة والتفسير المعطى، ولا تخترع آيات أو معاني خارجه.
            - title و description باللغة العربية، واذكر اسم السورة في العنوان.
            - تعامل مع النص القرآني باحترام، ولا تجعل نص آية صحيحة خياراً "خاطئاً".

            قواعد الأسئلة:
            - أنشئ 5 أسئلة اختيار من متعدد فقط.
            - كل سؤال يحتوي 4 خيارات: optionA, optionB, optionC, optionD.
            - correctAnswer يجب أن يكون فقط A أو B أو C أو D.
            - الأسئلة تختبر فهم معاني السورة ومقاصدها من التفسير المعطى، وليست أسئلة سطحية.
            - لا تسأل عن أرقام الآيات أو ترتيب السورة أو نوع النزول كأسئلة سطحية.
            - اجعل الإجابة الصحيحة مبنية على المعنى أو المقصد المستفاد من التفسير.
            """;

        String input = """
            الصعوبة: %s
            الفترة: %s

            مراجع سور مستخدمة مسبقاً ويجب تجنبها:
            %s

            محتوى السورة (استخدمه وحده):
            %s

            أرجع JSON فقط بهذا الشكل بالضبط:

            {
              "title": "string",
              "description": "string",
              "targetName": "string",
              "targetValue": 1,
              "verificationSource": "WHATSAPP",
              "verificationRule": "ANSWER_QUESTIONS",
              "verificationTarget": "string",
              "questions": [
                { "question": "string", "optionA": "string", "optionB": "string", "optionC": "string", "optionD": "string", "correctAnswer": "A" },
                { "question": "string", "optionA": "string", "optionB": "string", "optionC": "string", "optionD": "string", "correctAnswer": "B" },
                { "question": "string", "optionA": "string", "optionB": "string", "optionC": "string", "optionD": "string", "correctAnswer": "C" },
                { "question": "string", "optionA": "string", "optionB": "string", "optionC": "string", "optionD": "string", "correctAnswer": "D" },
                { "question": "string", "optionA": "string", "optionB": "string", "optionC": "string", "optionD": "string", "correctAnswer": "A" }
              ]
            }

            شروط مهمة جداً:
            - targetName يجب أن يساوي مرجع السورة المعطى (reference) بالضبط.
            - verificationTarget يجب أن يساوي targetName.
            - verificationSource يجب أن يكون WHATSAPP.
            - verificationRule يجب أن يكون ANSWER_QUESTIONS.
            - كل سؤال يجب أن يكون مرتبطاً بمعنى من التفسير المعطى، لا بنص الآية حرفياً.
            """.formatted(
                difficulty,
                period,
                existingChallenges,
                formatContent(contents)
        );

        return openAiClient.generate(instructions, input);
    }

    private String formatContent(List<IslamicContentDTO> contents) {
        StringBuilder builder = new StringBuilder();
        for (IslamicContentDTO c : contents) {
            builder.append("reference: ").append(c.getReference()).append("\n");
            builder.append("السورة: ").append(c.getSurahName()).append("\n");
            builder.append("الآيات: ").append(c.getFromAyah()).append("-").append(c.getToAyah()).append("\n");
            builder.append("النص: ").append(c.getArabicText()).append("\n");
            builder.append("التفسير: ").append(c.getTafsirSummary()).append("\n\n");
        }
        return builder.toString();
    }
}
