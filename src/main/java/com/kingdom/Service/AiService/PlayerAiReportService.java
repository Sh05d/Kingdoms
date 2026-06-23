package com.kingdom.Service.AiService;

import com.kingdom.API.ApiException;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Model.Player;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;

import java.nio.charset.StandardCharsets;

import java.io.File;
import java.nio.file.Files;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlayerAiReportService {
    private final OpenAiClient openAiClient;

    public record PlayerReport(byte[] pdf, String reportHtml) {}

    // Build the PDF AND keep the AI report HTML, so the email can embed the report inline (not just attach the PDF).
    public PlayerReport generatePlayerReport(Player player, List<KingdomMembership> memberships) {
        String aiReportHtml = generateAiReportHtml(player, memberships);
        String fullHtml = buildPdfHtml(player, aiReportHtml);
        return new PlayerReport(convertHtmlToPdf(fullHtml), aiReportHtml);
    }

    public byte[] generatePlayerReportPdf(Player player, List<KingdomMembership> memberships) {
        return generatePlayerReport(player, memberships).pdf();
    }

    private String generateAiReportHtml(Player player, List<KingdomMembership> memberships) {

        String instructions = """
                أنت محلل أداء داخل منصة اسمها "المملكة".

                مهمتك:
                إنشاء تقرير أداء للاعب بناءً على بياناته داخل الممالك.

                القواعد:
                - اكتب التقرير باللغة العربية.
                - أرجع HTML فقط.
                - لا تستخدم Markdown نهائياً.
                - ممنوع استخدام # أو ## أو ###.
                - لا تستخدم JSON.
                - لا تضف <!DOCTYPE html> أو html أو head أو body.
                - استخدم فقط هذه الوسوم: h2, h3, p, strong, span, br.
                - لا تستخدم ul أو li نهائياً.
                - لا تستخدم القوائم النقطية.
                - لا تستخدم style داخل HTML.
                - لا تضف معلومات غير موجودة في البيانات.
                - لا تبالغ في التحليل.
                - ركز على نقاط الخبرة والقسم والاستمرارية.
                - اجعل التقرير مناسباً للاعب عادي.
                - اجعل تحليل كل مملكة مختصراً جداً (سطر واحد قصير لكل مملكة) حتى لا يطول التقرير.
                - مهم جداً: يجب أن ينتهي التقرير دائماً بهذه الأقسام الثلاثة بالترتيب: نقاط القوة، ثم فرص التحسين، ثم خطة بسيطة للأسبوع القادم. لا تتركها أبداً ولا تتوقف قبل كتابتها.
                - لا تذكر أنك ذكاء اصطناعي.
                - إذا احتجت كتابة كلمة إنجليزية أو رقم داخل النص العربي فضعها داخل:
                  <span class="ltr">XP</span>
                """;

        String input = """
                بيانات اللاعب:
                الاسم: %s

                بيانات العضويات:
                %s

                المطلوب إنشاء تقرير HTML يحتوي على:
                1. ملخص عام
                2. أفضل مملكة أداءً
                3. تحليل كل مملكة
                4. نقاط القوة
                5. فرص التحسين
                6. خطة بسيطة للأسبوع القادم

                مثال الشكل المطلوب:

                <h2>ملخص عام</h2>
                <p>يظهر أداء اللاعب بشكل مبدئي داخل الممالك الحالية.</p>

                <h2>أفضل مملكة أداءً</h2>
                <p>النص هنا.</p>

                <h2>تحليل الممالك</h2>
                <h3>مملكة الألعاب</h3>
                <p><strong>نقاط الخبرة:</strong> <span class="ltr">0</span></p>
                <p><strong>القسم:</strong> <span class="ltr">3</span></p>
                <p><strong>الاستمرارية:</strong> <span class="ltr">0</span></p>

                مهم جداً:
                - أرجع HTML فقط بدون أي نص خارجه.
                - لا تستخدم Markdown.
                - لا تستخدم #.
                - لا تستخدم جدول.
                - لا تستخدم ul أو li.
                - لا تكتب DOCTYPE أو html أو body.
                - ممنوع استخدام أي كلمات إنجليزية في التقرير.
                استخدم اللغة العربية فقط في العناوين والتحليل.
                لا تكتب نوع المملكة بالإنجليزي.
                الأرقام فقط مسموحة داخل:<span class="ltr">...</span>.
                """.formatted(
                escapeHtml(player.getDisplayName()),
                formatMemberships(memberships)
        );

        String html = openAiClient.generate(instructions, input, 3000);

        if (html == null || html.isBlank()) {
            throw new ApiException("AI failed to generate player report");
        }

        return cleanAiHtml(html);
    }

    private String formatMemberships(List<KingdomMembership> memberships) {

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < memberships.size(); i++) {

            KingdomMembership membership = memberships.get(i);

            builder.append(i + 1).append(".\n");
            builder.append("المملكة: ").append(membership.getKingdom().getName()).append("\n");
            builder.append("نوع المملكة: ").append(membership.getKingdom().getType()).append("\n");
            builder.append("نقاط الخبرة: ").append(membership.getTotalXP()).append("\n");
            builder.append("القسم: ").append(membership.getDivision()).append("\n");
            builder.append("الاستمرارية: ").append(membership.getStreak()).append("\n");
            builder.append("تاريخ الانضمام: ").append(membership.getJoinedAt()).append("\n\n");
        }

        return builder.toString();
    }

    private String buildPdfHtml(Player player, String aiReportHtml) {

        return """
                <!DOCTYPE html>
                <html lang="ar" dir="rtl">
                <head>
                    <meta charset="UTF-8" />
                    <style>
                        @page { size: A4; margin: 0; }
                        html { direction: rtl; }
                        html, body { background-color: #0c0a1c; margin: 0; padding: 0; }
                        body {
                            font-family: "ArabicFont", "Tahoma", "Arial", sans-serif;
                            direction: rtl;
                            text-align: right;
                            color: #cdc8e6;
                            line-height: 1.95;
                            font-size: 13.5px;
                        }
                        * { box-sizing: border-box; }
                        html, body, div, p, h2, h3, strong {
                            direction: rtl;
                            text-align: right;
                            unicode-bidi: embed;
                        }
                        .page { padding: 34px 38px; }
                        .brandbar {
                            background-color: #4c1d95;
                            border-radius: 14px;
                            padding: 22px 26px;
                            margin-bottom: 24px;
                        }
                        .brand { color: #ffffff; font-size: 23px; font-weight: bold; margin: 0; }
                        .brandsub { color: #ddccff; font-size: 11.5px; margin: 6px 0 0; }
                        .reporttitle { color: #c9a9ff; font-size: 18px; font-weight: bold; margin: 0 0 16px; }
                        .metarow { color: #9b95c0; font-size: 11.5px; margin: 3px 0; }
                        .rule { height: 2px; background-color: #6d28d9; margin: 16px 0 22px; border-radius: 2px; }
                        h2 {
                            color: #c9a9ff;
                            font-size: 16px;
                            font-weight: bold;
                            margin-top: 24px;
                            margin-bottom: 9px;
                            padding: 9px 15px;
                            background-color: #181433;
                            border-right: 4px solid #a855f7;
                            border-radius: 8px;
                            direction: rtl;
                            text-align: right;
                            page-break-after: avoid;
                        }
                        h3 {
                            color: #f0ecfb;
                            font-size: 14px;
                            font-weight: bold;
                            margin-top: 16px;
                            margin-bottom: 6px;
                            direction: rtl;
                            text-align: right;
                            page-break-after: avoid;
                        }
                        p { margin: 7px 0; color: #cdc8e6; direction: rtl; text-align: right; }
                        strong { color: #fbbf24; font-weight: bold; }
                        .ltr {
                            direction: ltr;
                            unicode-bidi: embed;
                            display: inline-block;
                            color: #c084fc;
                            font-weight: bold;
                        }
                        .footer {
                            margin-top: 28px;
                            padding-top: 16px;
                            border-top: 1px solid #2a2350;
                            color: #6b6590;
                            font-size: 10.5px;
                            text-align: center;
                        }
                    </style>
                </head>
                <body dir="rtl">
                    <div class="page" dir="rtl">
                        <div class="brandbar" dir="rtl">
                            <p class="brand">الممالك</p>
                            <p class="brandsub">تقدّمٌ يُكتسب، لا يُدّعى</p>
                        </div>
                        <p class="reporttitle">تقرير أداء اللاعب</p>
                        <p class="metarow">اللاعب: %s</p>
                        <p class="metarow">تاريخ التقرير: %s</p>
                        <div class="rule"></div>
                        <div dir="rtl">
                            %s
                        </div>
                        <div class="footer">وثيقة صادرة آلياً من منصة الممالك</div>
                    </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(player.getDisplayName()),
                LocalDateTime.now(),
                aiReportHtml
        );
    }

    private byte[] convertHtmlToPdf(String html) {

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            PdfRendererBuilder builder = new PdfRendererBuilder();

            File arabicFont = findArabicFontFile();

            if (arabicFont != null) {
                builder.useFont(arabicFont, "ArabicFont");
            }

            builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
            builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
            builder.defaultTextDirection(BaseRendererBuilder.TextDirection.RTL);

            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();

            return outputStream.toByteArray();

        } catch (Exception e) {
            throw new ApiException("Failed to generate player AI report PDF: " + e.getMessage());
        }
    }

    private File findArabicFontFile() {

        String[] possiblePaths = {
                "C:/Windows/Fonts/arial.ttf",
                "C:/Windows/Fonts/tahoma.ttf",
                "C:/Windows/Fonts/arialuni.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/truetype/noto/NotoNaskhArabic-Regular.ttf"
        };

        for (String path : possiblePaths) {
            File font = new File(path);

            if (font.exists() && Files.isReadable(font.toPath())) {
                return font;
            }
        }

        return null;
    }

    private String cleanAiHtml(String html) {

        if (html == null || html.isBlank()) {
            throw new ApiException("AI returned empty HTML");
        }

        String rawHtml = html
                .replace("```html", "")
                .replace("```HTML", "")
                .replace("```", "")
                .replace("#", "")
                .replace("<!DOCTYPE html>", "")
                .replace("<html lang=\"ar\" dir=\"rtl\">", "")
                .replace("<html>", "")
                .replace("</html>", "")
                .replace("<head>", "")
                .replace("</head>", "")
                .replace("<body>", "")
                .replace("<body dir=\"rtl\">", "")
                .replace("</body>", "")
                .replace("<ul>", "")
                .replace("</ul>", "")
                .replace("<li>", "<p>")
                .replace("</li>", "</p>")
                .trim();

        Safelist safelist = Safelist.none()
                .addTags("h2", "h3", "p", "strong", "span", "br")
                .addAttributes("span", "class");

        Document.OutputSettings outputSettings = new Document.OutputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .charset(StandardCharsets.UTF_8)
                .prettyPrint(false);

        return Jsoup.clean(
                rawHtml,
                "",
                safelist,
                outputSettings
        );
    }

    private String escapeHtml(String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
