package com.kingdom.Service.AiService;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.KingdomType;
import com.kingdom.Enums.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class NutritionAiService implements KingdomAiService {

    private final OpenAiClient openAiClient;

    @Override
    public KingdomType kingdom() {
        return KingdomType.NUTRITION;
    }

    @Override
    public String generateChallenge(Difficulty difficulty, Period period, List<String> existingChallenges) {
        String instructions = """
            أنت مسؤول عن توليد تحديات لمملكة التغذية في منصة الممالك.

            أرجع JSON صحيح فقط.
            لا تستخدم Markdown.
            لا تضف أي شرح خارج JSON.

            القواعد الإلزامية:
            - التحدي يعتمد على رفع صورة وجبة فقط عبر الواتساب.
            - نوّع التحديات، لا تجعلها كلها "وجبة صحية" فقط.
            - أمثلة للتنوع:
              * وجبة متوازنة
              * وجبة غنية بالبروتين
              * وجبة تحتوي خضار
              * تقليل المشروبات السكرية
              * وجبة منزلية
              * طبق يحتوي مصدر كارب وبروتين ودهون صحية
              * وجبة خفيفة متوازنة
            - التحدي يشجع على اختيارات غذائية أفضل بدون حمية قاسية.
            - لا تطلب خسارة وزن.
            - لا تقدم نصائح طبية أو علاجية.
            - اذكر أن التحليل والقيم الغذائية تقديرية.
            - verificationSource دائماً "WHATSAPP".
            - verificationRule دائماً "FOOD_IMAGE_ANALYSIS".
            - verificationTarget دائماً "MEAL".
            - targetName دائماً "Meal Image Analysis".
            - targetValue دائماً 1.
            - title و description باللغة العربية.
            """;

        String input = """
            الصعوبة: %s
            الفترة: %s

            عناوين أو أهداف تحديات مستخدمة مسبقاً ويجب تجنب تكرارها:
            %s

            أنشئ تحدياً واحداً فقط بهذا الشكل بالضبط:

            {
              "title": "صوّر وجبة متوازنة",
              "description": "ارفع صورة لوجبتك عبر الواتساب وسيتم تحليلها تقديرياً من حيث الكاربوهيدرات والبروتين والدهون ومدى توازن الوجبة.",
              "targetName": "Meal Image Analysis",
              "targetValue": 1,
              "verificationSource": "WHATSAPP",
              "verificationRule": "FOOD_IMAGE_ANALYSIS",
              "verificationTarget": "MEAL"
            }

            شروط مهمة:
            - لا تكرر نفس فكرة التحديات السابقة.
            - اجعل العنوان قصير وواضح.
            - اجعل الوصف يوضح أن التحليل يشمل تقدير:
              الكاربوهيدرات، البروتين، الدهون، ومدى توازن الوجبة.
            - أرجع JSON فقط.
            """.formatted(difficulty, period, existingChallenges);

        return openAiClient.generate(instructions, input);
    }
}