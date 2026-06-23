package com.kingdom.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sends WhatsApp messages via the Twilio API and downloads inbound media. Config: twilio.* in
 * application.properties (real creds in the git-ignored application-local.properties).
 *
 * Feature-guarded: if twilio.enabled=false or creds are missing, the send methods no-op and return false.
 */
@Service
public class WhatsAppService {

    @Value("${twilio.enabled:false}")
    private boolean enabled;

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.whatsapp-from:}")
    private String whatsappFrom;

    // Verify the X-Twilio-Signature on inbound webhooks. Off by default so the local/ngrok demo is easy;
    // turn ON in production (and make sure the public URL Twilio calls is reconstructed correctly).
    @Value("${twilio.validate-signature:false}")
    private boolean validateSignature;

    // Cap inbound media size (WhatsApp media is well under this) to avoid buffering an oversized response.
    private static final int MAX_MEDIA_BYTES = 16 * 1024 * 1024;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** True only when Twilio is switched on AND all creds are present. */
    public boolean isEnabled() {
        return enabled && notBlank(accountSid) && notBlank(authToken) && notBlank(whatsappFrom);
    }

    /**
     * Send a WhatsApp text to a phone number (e.g. "+9665XXXXXXXX").
     * Returns true if Twilio accepted the message, false otherwise.
     */
    public boolean sendMessage(String toPhone, String text) {
        if (!isEnabled()) {
            return false;
        }
        try {
            String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid.trim() + "/Messages.json";

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("From", whatsappFrom.trim());
            form.add("To", "whatsapp:" + normalize(toPhone));
            form.add("Body", text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(accountSid.trim(), authToken.trim());

            JsonNode response = restTemplate.postForObject(url, new HttpEntity<>(form, headers), JsonNode.class);
            return response != null && response.path("sid").isTextual();
        } catch (Exception e) {
            return false;
        }
    }

    /** Convenience: send a verification code over WhatsApp. */
    public boolean sendOtp(String toPhone, String code) {
        return sendMessage(toPhone, "Your Kingdom verification code is: " + code);
    }

    /**
     * Send a WhatsApp interactive message from a Twilio Content Template (List Picker, Quick Reply, ...).
     * Pass the template's Content SID (HX...) and its variables ({"1": ..., "2": ...}); Twilio renders it.
     * Returns true if Twilio accepted it; no-ops (false) if Twilio is off or the SID is blank.
     */
    public boolean sendContentTemplate(String toPhone, String contentSid, Map<String, String> variables) {
        if (!isEnabled() || !notBlank(contentSid)) {
            return false;
        }
        try {
            String url = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid.trim() + "/Messages.json";

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("From", whatsappFrom.trim());
            form.add("To", "whatsapp:" + normalize(toPhone));
            form.add("ContentSid", contentSid.trim());
            if (variables != null && !variables.isEmpty()) {
                form.add("ContentVariables", objectMapper.writeValueAsString(variables));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(accountSid.trim(), authToken.trim());

            JsonNode response = restTemplate.postForObject(url, new HttpEntity<>(form, headers), JsonNode.class);
            return response != null && response.path("sid").isTextual();
        } catch (Exception e) {
            return false;
        }
    }

    /** Public helper: normalize any phone (incl. "whatsapp:05..") to E.164 (+9665..) for DB lookups. */
    public String normalizeToE164(String phone) {
        return normalize(phone);
    }

    /**
     * Download a Twilio media attachment (e.g. a PDF a user sent over WhatsApp) using Twilio Basic auth.
     *
     * SECURITY: the Twilio Basic-auth credentials are only attached when the URL is genuinely a Twilio host
     * (see {@link #isTwilioMediaHost}). Without this guard, an attacker hitting the public webhook with a
     * crafted MediaUrl0 could make us send our Twilio credentials to their server (credential leak) or fetch
     * internal addresses (SSRF). Any non-Twilio URL is rejected here.
     *
     * @return the raw bytes, or null if the URL is not a Twilio host / Twilio is unconfigured / the download fails.
     */
    public byte[] downloadMedia(String mediaUrl) {
        if (!isTwilioMediaHost(mediaUrl) || !notBlank(accountSid) || !notBlank(authToken)) {
            return null; // refuse to send credentials anywhere except a real Twilio host
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(accountSid.trim(), authToken.trim());
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    mediaUrl, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            byte[] body = response.getBody();
            return (body != null && body.length > MAX_MEDIA_BYTES) ? null : body; // reject oversized downloads
        } catch (Exception e) {
            return null;
        }
    }

    // Only api.twilio.com / *.twilio.com / *.twiliocdn.com over HTTPS are accepted media hosts.
    private boolean isTwilioMediaHost(String mediaUrl) {
        if (!notBlank(mediaUrl)) {
            return false;
        }
        try {
            URI u = URI.create(mediaUrl.trim());
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null) {
                return false;
            }
            String host = u.getHost().toLowerCase();
            return host.equals("api.twilio.com") || host.endsWith(".twilio.com") || host.endsWith(".twiliocdn.com");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Webhook gate: returns true if signature validation is OFF (config), or the X-Twilio-Signature is valid.
     * The caller passes the FULL public URL Twilio called + all POST params + the signature header.
     */
    public boolean passesSignatureCheck(String fullUrl, Map<String, String> params, String signature) {
        if (!validateSignature) {
            return true; // config-gated off (demo)
        }
        return isValidTwilioSignature(fullUrl, params, signature);
    }

    // Twilio's algorithm: HMAC-SHA1 of (url + each param key+value, sorted by key), keyed by the auth token,
    // Base64-encoded, compared to the X-Twilio-Signature header.
    private boolean isValidTwilioSignature(String fullUrl, Map<String, String> params, String signature) {
        if (!notBlank(authToken) || !notBlank(signature) || fullUrl == null) {
            return false;
        }
        try {
            StringBuilder data = new StringBuilder(fullUrl);
            for (Map.Entry<String, String> e : new TreeMap<>(params).entrySet()) {
                data.append(e.getKey()).append(e.getValue() == null ? "" : e.getValue());
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authToken.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] hash = mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(hash);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    // Normalize a phone to E.164. Saudi-friendly (folded in from the old second WhatsApp service): 05xxxxxxxx,
    // 5xxxxxxxx and 966xxxxxxxxx all become +966xxxxxxxxx. Returns the bare number (no "whatsapp:" prefix —
    // sendMessage adds that). Already-E.164 numbers (e.g. "+9665...") pass through unchanged.
    private String normalize(String phone) {
        String p = phone == null ? "" : phone.trim();
        if (p.startsWith("whatsapp:")) {
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

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
