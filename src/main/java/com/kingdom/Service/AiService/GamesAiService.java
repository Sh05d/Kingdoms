package com.kingdom.Service.AiService;

import com.kingdom.Enums.Difficulty;
import com.kingdom.Enums.KingdomType;
import com.kingdom.Enums.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GamesAiService implements com.kingdom.Service.AiService.KingdomAiService {
    private final com.kingdom.Service.AiService.OpenAiClient openAiClient;

    @Override
    public KingdomType kingdom() {
        return KingdomType.GAMING;
    }

    @Override
    public String generateChallenge(Difficulty difficulty, Period period, List<String> existingChallenges) {

        String instructions = """
            أنت مسؤول عن توليد تحديات لمملكة الألعاب في منصة تحفيزية.

            أرجع JSON صحيح فقط.
            لا تستخدم Markdown.
            لا تضف أي شرح خارج JSON.

            القواعد:
            - التحدي يجب أن يكون عاماً ومتاحاً لجميع اللاعبين.
            - استخدم ألعاب Steam حقيقية فقط.
            - لا تكرر أي عنوان من عناوين التحديات الحالية.
            - لا تنشئ تحدياً بنفس هدف تحدٍ موجود مسبقاً.
            - يجب أن يكون التحدي قابلاً للتحقق باستخدام بيانات Steam.
            - اجعل title و description باللغة العربية.
            - يجب أن يحتوي title على اسم اللعبة الرسمي بالإنجليزية بين قوسين.
            - اجعل title قصيراً وجذاباً.
            - اجعل description واضحاً ومباشراً.
            - verificationSource يجب أن يكون دائماً "STEAM".
                الألعاب المفضلة:
                
                يفضل اختيار التحديات من الألعاب التالية أولاً إذا كانت مناسبة للصعوبة والفترة المطلوبة:
                
                - Rocket League
                - Marvel Rivals
                - Phasmophobia
                - Dark Souls Remastered
                
                قواعد مهمة:
                
                - أعطِ أولوية لهذه الألعاب عند إنشاء التحديات.
                - لا تكرر نفس اللعبة بشكل مستمر.
                - نوّع بين الألعاب المفضلة عند الإمكان.
                - إذا لم تكن أي من هذه الألعاب مناسبة للصعوبة أو الفترة المطلوبة، اختر لعبة Steam حقيقية أخرى مناسبة.
                - لا تجبر التحدي على إحدى هذه الألعاب إذا كان سيؤدي إلى تحدٍ غير منطقي.
                - استمر في استخدام أي لعبة Steam حقيقية أخرى عند الحاجة.
                """;

        String input = """
            الصعوبة: %s
            الفترة: %s

            عناوين التحديات الحالية التي يجب تجنب تكرارها تماماً:
            %s

            قاعدة اختيار نوع التحقق:
            - إذا كانت الفترة DAILY استخدم غالباً PLAYTIME أو ACHIEVEMENT.
            - إذا كانت الفترة WEEKLY استخدم غالباً ACHIEVEMENT أو ACHIEVEMENT_COUNT.
            - إذا كانت الفترة MONTHLY استخدم غالباً ACHIEVEMENT_COUNT أو ACHIEVEMENT.
            - لا تستخدم PLAYTIME لكل التحديات.
            - نوّع بين PLAYTIME و ACHIEVEMENT و ACHIEVEMENT_COUNT.

            أنشئ تحدياً واحداً فقط بهذا الشكل:

            {
              "title": "رحلة الألغاز في بورتال 2 (Portal 2)",
              "description": "string",
              "targetName": "Portal 2",
              "targetValue": 120,
              "verificationSource": "STEAM",
              "verificationRule": "PLAYTIME",
              "verificationTarget": "Portal 2"
            }

            قواعد الحقول:
            - targetName يجب أن يحتوي على اسم اللعبة الرسمي بالإنجليزية.
            - verificationTarget يجب أن يكون باللغة الإنجليزية.
            - targetValue يجب أن يكون رقماً.
            - verificationSource يجب أن يكون دائماً "STEAM".

            قواعد الوقت:
            - إذا كانت مدة اللعب أقل من 60 دقيقة اذكرها بالدقائق.
            - إذا كانت مدة اللعب 60 دقيقة أو أكثر اذكرها بالساعات.
            - لا تذكر الدقائق في title أو description إذا أمكن التعبير عنها بالساعات.

            قواعد التحقق:
            - إذا كانت verificationRule = PLAYTIME:
              targetName = اسم اللعبة الرسمي بالإنجليزية.
              verificationTarget = اسم اللعبة الرسمي بالإنجليزية.
              targetValue = عدد الدقائق المطلوبة للعب.

            - إذا كانت verificationRule = ACHIEVEMENT:
              targetName = اسم اللعبة الرسمي بالإنجليزية.
              verificationTarget = اسم الإنجاز الرسمي بالإنجليزية.
              targetValue = 1.

            - إذا كانت verificationRule = ACHIEVEMENT_COUNT:
              targetName = اسم اللعبة الرسمي بالإنجليزية.
              verificationTarget = اسم اللعبة الرسمي بالإنجليزية.
              targetValue = عدد الإنجازات المطلوبة.
            """.formatted(difficulty, period, existingChallenges);

        return openAiClient.generate(instructions, input);
    }
}
