package com.kingdom.Service.APIService;

import com.kingdom.Model.Player;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends transactional emails via the Mailtrap HTTP Sending API (mailtrap.* in application.properties; the real
 * token lives in the git-ignored application-local.properties). Branded HTML templates: a general card
 * (welcome / AI report / lobby notifications) and the richer Arabic lobby-invite templates.
 *
 * Feature-guarded: if mailtrap.enabled=false or the token is missing, the send methods no-op and return false,
 * so the app runs fine without email configured. Best-effort: a send failure never throws.
 */
@Service
public class EmailService {

    @Value("${mailtrap.enabled:false}")
    private boolean enabled;

    @Value("${mailtrap.api-token:}")
    private String apiToken;

    @Value("${mailtrap.api-url:https://send.api.mailtrap.io/api/send}")
    private String apiUrl;

    @Value("${mailtrap.from-email:no-reply@kingdoms.app}")
    private String fromEmail;

    @Value("${mailtrap.from-name:Kingdoms}")
    private String fromName;

    private final RestTemplate restTemplate = new RestTemplate();

    /** True only when Mailtrap is switched on AND the API token is present. */
    public boolean isEnabled() {
        return enabled && notBlank(apiToken);
    }

    /** Generic: wrap a plain message in the branded card and send it as HTML. Best-effort (never throws). */
    public boolean send(String to, String subject, String body) {
        return post(to, subject, buildHtmlTemplate(body), null, null);
    }

    /** Welcome email (Arabic), sent right after a user verifies their phone. */
    public boolean sendWelcome(String toEmail, String username) {
        String name = notBlank(username) ? username : "بطل";
        return send(toEmail, "مرحبًا بك في الممالك 👑",
                "مرحبًا " + name + "،\n\nتم التحقق من رقم هاتفك وحسابك جاهز الآن في الممالك. "
                        + "اختر مملكة، ابدأ تحدياً، وابدأ بكسب نقاط الخبرة!");
    }

    /** AI player-performance report (Arabic) with the generated PDF attached. Called by PlayerService. */
    public boolean sendPlayerAiReport(Player player, byte[] pdf, String reportHtml) {
        if (player == null || player.getUser() == null) {
            return false;
        }
        String name = (player.getDisplayName() == null || player.getDisplayName().isBlank())
                ? "بطل" : player.getDisplayName();
        // Embed the AI report sections inline in the email body AND attach the PDF.
        String html = buildAiReportEmail(name, reportHtml == null ? "" : reportHtml);
        return post(player.getUser().getEmail(), "تقرير أدائك في الممالك 📊", html, pdf, "player-ai-report.pdf");
    }

    /** Send one HTML email with a PDF attachment (base64, via Mailtrap). The card wraps the message. Best-effort. */
    public boolean sendWithPdfAttachment(String to, String subject, String body, byte[] pdfBytes, String fileName) {
        return post(to, subject, buildHtmlTemplate(body), pdfBytes, fileName == null ? "attachment.pdf" : fileName);
    }

    /** Rich Arabic lobby-invite email (public lobby — no code). */
    public void sendLobbyInvite(String to, String playerName, String hostName, String lobbyName, String kingdomName,
                                String challengeDescription, String startDate, String startTime) {
        post(to, "دعوة للانضمام إلى تحدٍ جديد في الممالك",
                buildLobbyInviteTemplate(playerName, hostName, lobbyName, kingdomName,
                        challengeDescription, startDate, startTime), null, null);
    }

    /** Rich Arabic lobby-invite email (private lobby — with the join code). */
    public void sendLobbyInvite(String to, String playerName, String hostName, String lobbyName, String kingdomName,
                                String challengeDescription, String startDate, String startTime, String inviteCode) {
        post(to, "دعوة للانضمام إلى تحدٍ جديد في الممالك",
                buildLobbyInviteWithCodeTemplate(playerName, hostName, lobbyName, kingdomName,
                        challengeDescription, startDate, startTime, inviteCode), null, null);
    }

    // One Mailtrap POST. Attaches a base64 PDF when pdfBytes is provided. No-op if Mailtrap/'to' is missing.
    private boolean post(String toEmail, String subject, String html, byte[] pdfBytes, String fileName) {
        if (!isEnabled() || !notBlank(toEmail)) {
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiToken.trim());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("from", Map.of("email", fromEmail, "name", fromName));
            body.put("to", List.of(Map.of("email", toEmail)));
            body.put("subject", subject);
            body.put("html", html);
            if (pdfBytes != null && pdfBytes.length > 0) {
                body.put("attachments", List.of(Map.of(
                        "content", Base64.getEncoder().encodeToString(pdfBytes),
                        "filename", (fileName == null ? "attachment.pdf" : fileName),
                        "type", "application/pdf",
                        "disposition", "attachment")));
            }

            ResponseEntity<String> response =
                    restTemplate.postForEntity(apiUrl, new HttpEntity<>(body, headers), String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private String buildHtmlTemplate(String body) {
        String formattedBody = body.replace("\n", "<br>");
        return """
                <!DOCTYPE html>
                <html lang="ar" dir="rtl">
                <head><meta charset="UTF-8"></head>
                <body style="margin:0; padding:0; background-color:#0a0916; font-family:'Segoe UI','Tahoma',Arial,sans-serif;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#0a0916; padding:36px 12px;">
                        <tr>
                            <td align="center">
                                <table width="560" cellpadding="0" cellspacing="0" dir="rtl" style="max-width:560px; background-color:#14112b; border-radius:20px; overflow:hidden; border:1px solid rgba(168,85,247,0.30);">
                                    <tr>
                                        <td bgcolor="#5b21b6" style="background-color:#5b21b6; background-image:linear-gradient(120deg,#3a1772,#7c3aed); padding:28px 36px; border-bottom:1px solid rgba(168,85,247,0.45);">
                                            <div style="font-size:25px; font-weight:bold; color:#ffffff; letter-spacing:0.5px;">👑 الممالك</div>
                                            <div style="font-size:12.5px; color:#e3d6ff; margin-top:6px;">تقدّمٌ يُكتسب، لا يُدّعى</div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:36px 38px 8px;">
                                            <p style="margin:0; color:#e9e5fb; font-size:15.5px; line-height:2.0; direction:rtl; text-align:right;">&rlm;%s</p>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:22px 38px 0;">
                                            <div style="height:2px; background-color:#a855f7; border-radius:2px;"></div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:16px 38px 30px;">
                                            <p style="margin:0; color:#8b84b3; font-size:12px; line-height:1.7; direction:rtl; text-align:right;">هذه رسالة آلية من منصة الممالك — الرجاء عدم الرد على هذا البريد.</p>
                                        </td>
                                    </tr>
                                </table>
                                <div style="color:#4f4977; font-size:11px; margin-top:18px; letter-spacing:3px;">K I N G D O M S</div>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(formattedBody);
    }

    // Dark-violet email that embeds the AI report sections (نقاط القوة / فرص التحسين / الخطة) inline, with the PDF
    // also attached. The <style> block themes the report's h2/h3/p/strong for the dark card.
    private String buildAiReportEmail(String name, String reportHtml) {
        return """
                <!DOCTYPE html>
                <html lang="ar" dir="rtl">
                <head><meta charset="UTF-8">
                <style>
                    .report h2 { color:#c9a9ff; font-size:16px; font-weight:bold; margin:22px 0 8px; padding:9px 15px; background-color:#181433; border-right:4px solid #a855f7; border-radius:8px; direction:rtl; text-align:right; }
                    .report h3 { color:#f0ecfb; font-size:14px; font-weight:bold; margin:14px 0 6px; direction:rtl; text-align:right; }
                    .report p { color:#cdc8e6; font-size:14px; line-height:1.9; margin:7px 0; direction:rtl; text-align:right; }
                    .report strong { color:#fbbf24; font-weight:bold; }
                    .report .ltr { direction:ltr; unicode-bidi:embed; color:#c084fc; font-weight:bold; display:inline-block; }
                </style>
                </head>
                <body style="margin:0; padding:0; background-color:#0a0916; font-family:'Segoe UI','Tahoma',Arial,sans-serif;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#0a0916; padding:36px 12px;">
                        <tr><td align="center">
                            <table width="600" cellpadding="0" cellspacing="0" dir="rtl" style="max-width:600px; background-color:#14112b; border-radius:20px; overflow:hidden; border:1px solid rgba(168,85,247,0.30);">
                                <tr>
                                    <td bgcolor="#5b21b6" style="background-color:#5b21b6; background-image:linear-gradient(120deg,#3a1772,#7c3aed); padding:28px 36px; border-bottom:1px solid rgba(168,85,247,0.45);">
                                        <div style="font-size:25px; font-weight:bold; color:#ffffff; letter-spacing:0.5px;">👑 الممالك</div>
                                        <div style="font-size:12.5px; color:#e3d6ff; margin-top:6px;">تقرير أدائك — تقدّمٌ يُكتسب، لا يُدّعى</div>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:30px 38px 10px;">
                                        <p style="margin:0 0 4px; color:#e9e5fb; font-size:15.5px; line-height:1.9; direction:rtl; text-align:right;">&rlm;مرحبًا %s 👑</p>
                                        <p style="margin:0; color:#9b95c0; font-size:13px; line-height:1.8; direction:rtl; text-align:right;">هذا تقرير أدائك داخل الممالك (نسخة PDF مرفقة أيضاً).</p>
                                        <div style="height:2px; background-color:#a855f7; border-radius:2px; margin:18px 0 4px;"></div>
                                        <div class="report" dir="rtl" style="direction:rtl; text-align:right;">%s</div>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:8px 38px 30px;">
                                        <p style="margin:0; color:#8b84b3; font-size:12px; line-height:1.7; direction:rtl; text-align:right;">القيم تقديرية بناءً على نشاطك — رسالة آلية، الرجاء عدم الرد.</p>
                                    </td>
                                </tr>
                            </table>
                            <div style="color:#4f4977; font-size:11px; margin-top:18px; letter-spacing:3px;">K I N G D O M S</div>
                        </td></tr>
                    </table>
                </body>
                </html>
                """.formatted(name, reportHtml);
    }

    private String buildLobbyInviteTemplate(String playerName, String hostName, String lobbyName, String kingdomName,
                                            String challengeDescription, String startDate, String startTime) {
        return """
                <!DOCTYPE html>
                <html lang="ar" dir="rtl">
                <head>
                <meta charset="UTF-8">
                </head>
                <body style="margin:0; padding:0; background-color:#020817; font-family:'Tahoma','Segoe UI',Arial,sans-serif;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="padding:32px 0;">
                        <tr>
                            <td align="center">
                                <table width="560" cellpadding="0" cellspacing="0" dir="rtl" style="background-color:#0b1020; border-radius:16px; overflow:hidden; border:1px solid rgba(80,120,180,0.22);">
                                    <tr>
                                        <td style="background-color:#0b1020; padding:28px 36px; border-bottom:1px solid rgba(80,120,180,0.18);">
                                            <div style="font-size:20px; font-weight:bold; color:#f8fafc;">الممالك</div>
                                            <div style="font-size:13px; color:#94a3b8; margin-top:2px;">وسّع حدود مملكتك مع كل إنجاز</div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:32px 40px 32px 36px;">
                                            <div style="margin:0 0 8px; color:#f8fafc; font-size:16px; direction:rtl; text-align:right;">&rlm;مرحباً <span style="font-weight:bold;">%s</span>،</div>
                                            <div style="margin:0 0 24px; color:#94a3b8; font-size:14px; line-height:1.9; direction:rtl; text-align:right;">&rlm;تمت دعوتك للانضمام إلى تحدٍ جديد في الممالك!</div>
                                            <div style="margin:0 0 24px; color:#cbd5e1; font-size:14px; line-height:1.9; direction:rtl; text-align:right;">&rlm;%s دعاك للانضمام إلى لوبي "%s" ضمن %s.</div>
                                            <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:rgba(124,58,237,0.08); border:1px solid rgba(124,58,237,0.3); border-radius:12px; margin-bottom:20px;">
                                                <tr>
                                                    <td style="padding:20px 28px;">
                                                        <div style="color:#c4b5fd; font-size:13px; font-weight:bold; letter-spacing:0.5px; margin-bottom:16px;">تفاصيل التحدي</div>
                                                        <table width="100%%" cellpadding="0" cellspacing="0">
                                                            <tr><td style="padding:7px 0; color:#94a3b8; font-size:13px; white-space:nowrap;">المملكة</td><td style="padding:7px 0; color:#f8fafc; font-size:13px; font-weight:bold; text-align:left;">%s</td></tr>
                                                            <tr><td style="padding:7px 0; color:#94a3b8; font-size:13px; white-space:nowrap;">اسم اللوبي</td><td style="padding:7px 0; color:#f8fafc; font-size:13px; font-weight:bold; text-align:left;">%s</td></tr>
                                                            <tr><td style="padding:7px 0; color:#94a3b8; font-size:13px; white-space:nowrap;">التحدي</td><td style="padding:7px 0; color:#f8fafc; font-size:13px; font-weight:bold; text-align:left;">%s</td></tr>
                                                            <tr><td style="padding:7px 0; color:#94a3b8; font-size:13px; white-space:nowrap;">يبدأ في</td><td style="padding:7px 0; color:#f8fafc; font-size:13px; font-weight:bold; text-align:left;">%s</td></tr>
                                                            <tr><td style="padding:7px 0; color:#94a3b8; font-size:13px; white-space:nowrap;">الساعة</td><td style="padding:7px 0; color:#f8fafc; font-size:13px; font-weight:bold; text-align:left; direction:ltr; unicode-bidi:bidi-override;">%s</td></tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                            </table>
                                            <div style="margin:24px 0 0; color:#94a3b8; font-size:14px; line-height:1.9; direction:rtl; text-align:right;">&rlm;نتمنى لك تجربة ممتعة ومنافسة مليئة بالإنجازات.</div>
                                            <div style="margin:8px 0 0; color:#f8fafc; font-size:14px; font-weight:bold; direction:rtl; text-align:right;">&rlm;فريق الممالك</div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:18px 36px; background-color:#070f20; border-top:1px solid rgba(80,120,180,0.18);">
                                            <p style="margin:0; color:#64748b; font-size:12px; text-align:center;">هذه رسالة آلية، يرجى عدم الرد عليها</p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(playerName, hostName, lobbyName, kingdomName, kingdomName, lobbyName, challengeDescription, startDate, startTime);
    }

    private String buildLobbyInviteWithCodeTemplate(String playerName, String hostName, String lobbyName, String kingdomName,
                                                    String challengeDescription, String startDate, String startTime, String inviteCode) {
        return """
                <!DOCTYPE html>
                <html lang="ar" dir="rtl">
                <head>
                <meta charset="UTF-8">
                </head>
                <body style="margin:0; padding:0; background-color:#020817; font-family:'Tahoma','Segoe UI',Arial,sans-serif;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="padding:32px 0;">
                        <tr>
                            <td align="center">
                                <table width="560" cellpadding="0" cellspacing="0" dir="rtl" style="background-color:#0b1020; border-radius:16px; overflow:hidden; border:1px solid rgba(80,120,180,0.22);">
                                    <tr>
                                        <td style="background-color:#0b1020; padding:28px 36px; border-bottom:1px solid rgba(80,120,180,0.18);">
                                            <div style="font-size:20px; font-weight:bold; color:#f8fafc;">الممالك</div>
                                            <div style="font-size:13px; color:#94a3b8; margin-top:2px;">وسّع حدود مملكتك مع كل إنجاز</div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:32px 40px 32px 36px;">
                                            <div style="margin:0 0 8px; color:#f8fafc; font-size:16px; direction:rtl; text-align:right;">&rlm;مرحباً <span style="font-weight:bold;">%s</span>،</div>
                                            <div style="margin:0 0 24px; color:#94a3b8; font-size:14px; line-height:1.9; direction:rtl; text-align:right;">&rlm;تمت دعوتك للانضمام إلى تحدٍ جديد في الممالك!</div>
                                            <div style="margin:0 0 24px; color:#cbd5e1; font-size:14px; line-height:1.9; direction:rtl; text-align:right;">&rlm;%s دعاك للانضمام إلى لوبي "%s" ضمن %s.</div>
                                            <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:rgba(250,204,21,0.08); border:2px solid #facc15; border-radius:12px; margin-bottom:20px;">
                                                <tr>
                                                    <td align="center" style="padding:24px 28px;">
                                                        <div style="color:#facc15; font-size:13px; font-weight:bold; letter-spacing:0.5px; margin-bottom:12px;">كود الدخول للوبي</div>
                                                        <div style="display:inline-block; background-color:#facc15; color:#0b1020; font-size:28px; font-weight:bold; letter-spacing:6px; padding:14px 28px; border-radius:10px; direction:ltr; unicode-bidi:bidi-override;">%s</div>
                                                        <div style="color:#94a3b8; font-size:12px; margin-top:12px;">استخدم هذا الكود لتأكيد دخولك للوبي داخل التطبيق</div>
                                                    </td>
                                                </tr>
                                            </table>
                                            <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:rgba(124,58,237,0.08); border:1px solid rgba(124,58,237,0.3); border-radius:12px; margin-bottom:20px;">
                                                <tr>
                                                    <td style="padding:20px 28px;">
                                                        <div style="color:#c4b5fd; font-size:13px; font-weight:bold; letter-spacing:0.5px; margin-bottom:16px;">تفاصيل التحدي</div>
                                                        <table width="100%%" cellpadding="0" cellspacing="0">
                                                            <tr><td style="padding:7px 0; color:#94a3b8; font-size:13px; white-space:nowrap;">المملكة</td><td style="padding:7px 0; color:#f8fafc; font-size:13px; font-weight:bold; text-align:left;">%s</td></tr>
                                                            <tr><td style="padding:7px 0; color:#94a3b8; font-size:13px; white-space:nowrap;">اسم اللوبي</td><td style="padding:7px 0; color:#f8fafc; font-size:13px; font-weight:bold; text-align:left;">%s</td></tr>
                                                            <tr><td style="padding:7px 0; color:#94a3b8; font-size:13px; white-space:nowrap;">التحدي</td><td style="padding:7px 0; color:#f8fafc; font-size:13px; font-weight:bold; text-align:left;">%s</td></tr>
                                                            <tr><td style="padding:7px 0; color:#94a3b8; font-size:13px; white-space:nowrap;">يبدأ في</td><td style="padding:7px 0; color:#f8fafc; font-size:13px; font-weight:bold; text-align:left;">%s</td></tr>
                                                            <tr><td style="padding:7px 0; color:#94a3b8; font-size:13px; white-space:nowrap;">الساعة</td><td style="padding:7px 0; color:#f8fafc; font-size:13px; font-weight:bold; text-align:left; direction:ltr; unicode-bidi:bidi-override;">%s</td></tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                            </table>
                                            <div style="margin:24px 0 0; color:#94a3b8; font-size:14px; line-height:1.9; direction:rtl; text-align:right;">&rlm;نتمنى لك تجربة ممتعة ومنافسة مليئة بالإنجازات.</div>
                                            <div style="margin:8px 0 0; color:#f8fafc; font-size:14px; font-weight:bold; direction:rtl; text-align:right;">&rlm;فريق الممالك</div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:18px 36px; background-color:#070f20; border-top:1px solid rgba(80,120,180,0.18);">
                                            <p style="margin:0; color:#64748b; font-size:12px; text-align:center;">هذه رسالة آلية، يرجى عدم الرد عليها</p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(playerName, hostName, lobbyName, kingdomName, inviteCode, kingdomName, lobbyName, challengeDescription, startDate, startTime);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
