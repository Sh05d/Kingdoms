package com.kingdom.verification;

import com.kingdom.Service.AiService.OpenAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VOLUNTEER verification: confirms a player did volunteer work by sending their uploaded PDF certificate to
 * the AI ({@link OpenAiClient}) and asking whether it is a genuine certificate (optionally matching the
 * challenge's activity). The AI returns {approved, matchScore 0-100, reason}; we approve only when the model
 * approves AND the score meets volunteer.match-threshold.
 */
@Service
@RequiredArgsConstructor
public class VolunteerVerificationService {

    private final OpenAiClient openAiClient;

    // 0..1 (e.g. 0.5 means the matchScore must be at least 50). Lenient: a genuine cert with the player's name passes.
    @Value("${volunteer.match-threshold:0.5}")
    private double matchThreshold;

    private static final Pattern APPROVED = Pattern.compile("\"approved\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCORE = Pattern.compile("\"matchScore\"\\s*:\\s*(\\d+)");
    private static final Pattern REASON = Pattern.compile("\"reason\"\\s*:\\s*\"([^\"]*)\"");

    /**
     * Verify an uploaded certificate PDF.
     * @param nameHint the player's first name to look for on the certificate; may be null.
     * @return a small map: {approved, matchScore, reason}.
     */
    public Map<String, Object> verifyCertificate(byte[] pdfBytes, String filename, String nameHint) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Cheap sanity check: a real PDF starts with "%PDF-". Reject anything else before spending an AI call.
        if (pdfBytes == null || pdfBytes.length < 5
                || pdfBytes[0] != '%' || pdfBytes[1] != 'P' || pdfBytes[2] != 'D' || pdfBytes[3] != 'F') {
            result.put("approved", false);
            result.put("matchScore", 0);
            result.put("reason", "The uploaded file is not a PDF");
            return result;
        }

        String instructions = "تتحقق من شهادات العمل التطوعي. قرّر إن كان ملف الـPDF المرفوع شهادة أو خطابًا رسميًا "
                + "حقيقيًا يثبت أن الشخص قام بعمل تطوعي. المطلوب فقط: أن يبدو المستند شهادة/خطاب تطوع حقيقيًا ويذكر "
                + "كلمة \"تطوع\" أو \"volunteer\""
                + (nameHint != null && !nameHint.isBlank() ? ("، وأن يظهر فيه اسم المتطوع متضمنًا: \"" + nameHint + "\"") : "")
                + ". ليس مطلوبًا أن يذكر المستند اسم أي تحدٍّ أو نشاط محدد؛ والساعات والتواريخ والتوقيع مفيدة لكنها ليست "
                + "شرطًا. كن متساهلًا مع الشهادات الحقيقية، وارفض فقط المستندات الفارغة أو غير المتعلقة أو المزيّفة بوضوح. "
                + "Return ONLY strict JSON: {\"approved\": true|false, \"matchScore\": 0-100, \"reason\": \"سبب قصير بالعربية\"}.";

        String aiText = openAiClient.generateWithPdf(instructions,
                "Here is the uploaded certificate. Assess it and return the JSON.", pdfBytes, filename);

        if (aiText == null) {
            result.put("approved", false);
            result.put("matchScore", 0);
            result.put("reason", "AI is off/unconfigured, or the PDF could not be read");
            return result;
        }

        // Pull the fields out of the model's JSON (regex keeps it simple + tolerant of code-fences).
        int score = 0;
        Matcher s = SCORE.matcher(aiText);
        if (s.find()) {
            try {
                score = Math.max(0, Math.min(100, Integer.parseInt(s.group(1))));
            } catch (Exception ignored) {
                score = 0;
            }
        }
        boolean modelApproved = false;
        Matcher a = APPROVED.matcher(aiText);
        if (a.find()) {
            modelApproved = Boolean.parseBoolean(a.group(1));
        }
        String reason = "";
        Matcher r = REASON.matcher(aiText);
        if (r.find()) {
            reason = r.group(1);
        }

        // Approve only if the model says yes AND the score clears our threshold.
        boolean approved = modelApproved && score >= (int) Math.round(matchThreshold * 100);
        result.put("approved", approved);
        result.put("matchScore", score);
        result.put("reason", reason.isBlank() ? aiText.trim() : reason);
        return result;
    }
}
