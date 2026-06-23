package com.kingdom.Service.AiService;

import com.kingdom.API.ApiException;
import com.kingdom.Model.Player;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RecommendationAiService {
    private final OpenAiClient openAiClient;

    private static final Pattern KINGDOM_TYPE_PATTERN =
            Pattern.compile("\"kingdomType\"\\s*:\\s*\"([^\"]+)\"");

    private static final Pattern REASON_PATTERN =
            Pattern.compile("\"reason\"\\s*:\\s*\"([^\"]+)\"");

    public String[] recommendKingdom(
            Player player,
            String interest,
            List<String> joinedTypes,
            List<String> availableTypes
    ) {

        String instructions = buildInstructions();

        String input = buildInput(
                player,
                interest,
                joinedTypes,
                availableTypes
        );

        String json = openAiClient.generate(instructions, input);

        return parseRecommendationJson(json);
    }

    private String buildInstructions() {

        return """
            أنت مساعد ذكي داخل منصة اسمها "المملكة".

            مهمتك:
            تحليل اهتمام اللاعب المكتوب كنص حر، ثم اقتراح أقرب مملكة مناسبة من الممالك المتاحة.

            أرجع JSON صحيح فقط.
            لا تستخدم Markdown.
            لا تضف أي شرح خارج JSON.
            لا تضف تعليقات داخل JSON.

            القواعد:
            - لا تعتمد على كلمات مفتاحية فقط.
            - افهم المعنى والسياق من كلام اللاعب.
            - اختر أقرب مملكة مناسبة حتى لو لم تكن الكلمات متطابقة.
            - اختر kingdomType واحد فقط من قائمة الممالك المتاحة.
            - لا تختر أي مملكة من الممالك التي اللاعب مشترك فيها حاليًا.
            - لا تختر نوعًا خارج قائمة الممالك المتاحة.
            - kingdomType يجب أن يكون بالإنجليزي مثل enum تمامًا.
            - reason يجب أن يكون باللغة العربية.
            - reason يجب أن يكون مختصرًا وواضحًا ومبنيًا على اهتمام اللاعب.
            """;
    }

    private String buildInput(Player player, String interest, List<String> joinedTypes, List<String> availableTypes) {

        return """
            معلومات اللاعب:
            - اسم اللاعب: %s

            اهتمام اللاعب:
            "%s"

            الممالك التي اللاعب مشترك فيها حاليًا:
            %s

            الممالك المتاحة للاقتراح:
            %s

            معنى أنواع الممالك:
            READING = مملكة القراءة، الكتب، الروايات، المعرفة، تطوير الذات بالقراءة
            FAITH = مملكة الإسلاميات، القرآن، الأذكار، الفقه، السيرة، الثقافة الإسلامية
            SPORTS = مملكة الرياضة، الحركة، الصحة الجسدية، المشي، التمارين، اللياقة
            ART = مملكة الفن، الرسم، التصميم، الإبداع، التصوير، الحرف
            VOLUNTEERING = مملكة التطوع، مساعدة الآخرين، الأثر المجتمعي، الأعمال الخيرية
            GAMING = مملكة الألعاب، التحديات الرقمية، الألعاب، Steam، PlayStation، الإنجازات

            المطلوب:
            اختر أقرب مملكة مناسبة من قائمة الممالك المتاحة فقط.

            أمثلة فهم المعنى:
            - "أبغى أتحرك وأحسن صحتي" معناها SPORTS.
            - "أحب أقرأ وأتعلم من الكتب" معناها READING.
            - "أبغى أطور علاقتي بالقرآن" معناها FAITH.
            - "أحب الرسم والتصميم" معناها ART.
            - "أبغى أساعد الناس" معناها VOLUNTEERING.
            - "أحب ألعب وأفتح إنجازات" معناها GAMING.

            أرجع JSON فقط بهذا الشكل:

            {
              "kingdomType": "SPORTS",
              "reason": "تم اقتراح مملكة الرياضة لأنها الأقرب لاهتمامك بالحركة وتحسين صحة الجسم."
            }

            شروط مهمة:
            - kingdomType يجب أن يساوي قيمة واحدة من قائمة الممالك المتاحة بالضبط.
            - لا تستخدم اسم المملكة العربي داخل kingdomType.
            - reason يجب أن يكون باللغة العربية.
            - لا تضف أي نص خارج JSON.
            """.formatted(player.getDisplayName(), interest, joinedTypes, availableTypes);
    }

    private String[] parseRecommendationJson(String json) {

        if (json == null || json.isBlank()) {
            throw new ApiException("AI returned empty response");
        }

        json = json
                .replace("```json", "")
                .replace("```", "")
                .trim();

        Matcher kingdomTypeMatcher = KINGDOM_TYPE_PATTERN.matcher(json);
        Matcher reasonMatcher = REASON_PATTERN.matcher(json);

        if (!kingdomTypeMatcher.find()) {
            throw new ApiException("AI response did not contain kingdomType");
        }

        if (!reasonMatcher.find()) {
            throw new ApiException("AI response did not contain reason");
        }

        String kingdomType = kingdomTypeMatcher.group(1);
        String reason = reasonMatcher.group(1);

        return new String[]{kingdomType, reason};
    }
}
