package com.kingdom.Service.AiService;

import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared OpenAI client — the single engine every kingdom AI calls. Talks to the OpenAI Responses API
 * (model gpt-5.5), mirroring the Wafferha pattern.
 *
 * Config (application.properties / application-local.properties):
 *   openai.enabled, openai.api-key, openai.model, openai.responses-url
 *
 * Feature-guarded: if AI is disabled, unconfigured, or the call fails, the methods return null so callers
 * can fall back deterministically (never throws).
 */
@Component
public class OpenAiClient {

    @Value("${openai.enabled:false}")
    private boolean enabled;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.model:gpt-5.5}")
    private String model;

    @Value("${openai.responses-url:https://api.openai.com/v1/responses}")
    private String responsesUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /** True only when AI is switched on AND an API key is present. */
    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    /**
     * Ask the model to produce text. {@code instructions} = the system role/rubric;
     * {@code input} = the user/content payload.
     * Returns the model's output text, or null if AI is off / unconfigured / errored.
     */
    public String generate(String instructions, String input) {
        return generate(instructions, input, 1000);
    }

    /** Same, but with an explicit output-token budget (long outputs like the multi-kingdom player report need more). */
    public String generate(String instructions, String input, int maxOutputTokens) {
        if (!isEnabled()) {
            return null;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model.trim());
            body.put("instructions", instructions);
            body.put("input", input);
            body.put("max_output_tokens", maxOutputTokens);
            Map<String, Object> reasoning = new LinkedHashMap<>();
            reasoning.put("effort", "low");
            body.put("reasoning", reasoning);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey.trim());
            headers.setContentType(MediaType.APPLICATION_JSON);

            JsonNode response = restTemplate.postForObject(
                    responsesUrl.trim(),
                    new HttpEntity<>(body, headers),
                    JsonNode.class);
            return extractOutputText(response);
        } catch (Exception e) {
            // fallback-friendly: never throw on AI failure
            return null;
        }
    }

    /**
     * Ask the model about a PDF. Sends {@code instructions} (system rubric) + {@code userText} plus the PDF
     * itself (as a base64 input_file) to the Responses API, and returns the model's output text — or null if
     * AI is off / unconfigured / errored. Used by the Volunteer kingdom to verify a certificate.
     */
    public String generateWithPdf(String instructions, String userText, byte[] pdfBytes, String filename) {
        if (!isEnabled() || pdfBytes == null || pdfBytes.length == 0) {
            return null;
        }
        try {
            String dataUri = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(pdfBytes);

            Map<String, Object> fileItem = new LinkedHashMap<>();
            fileItem.put("type", "input_file");
            fileItem.put("filename", (filename == null || filename.isBlank()) ? "upload.pdf" : filename);
            fileItem.put("file_data", dataUri);

            Map<String, Object> textItem = new LinkedHashMap<>();
            textItem.put("type", "input_text");
            textItem.put("text", userText == null ? "" : userText);

            Map<String, Object> message = new LinkedHashMap<>();
            message.put("role", "user");
            message.put("content", List.of(textItem, fileItem));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model.trim());
            body.put("instructions", instructions);
            body.put("input", List.of(message));
            body.put("max_output_tokens", 1000);
            Map<String, Object> reasoning = new LinkedHashMap<>();
            reasoning.put("effort", "low");
            body.put("reasoning", reasoning);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey.trim());
            headers.setContentType(MediaType.APPLICATION_JSON);

            JsonNode response = restTemplate.postForObject(
                    responsesUrl.trim(), new HttpEntity<>(body, headers), JsonNode.class);
            return extractOutputText(response);
        } catch (Exception e) {
            return null;
        }
    }

    /** Pull the text out of the Responses API payload (output_text, else output[].content[].text). */
    private String extractOutputText(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode direct = response.path("output_text");
        if (direct.isTextual()) {
            return direct.asText();
        }
        for (JsonNode outputItem : response.path("output")) {
            for (JsonNode contentItem : outputItem.path("content")) {
                JsonNode textNode = contentItem.path("text");
                if (textNode.isTextual()) {
                    return textNode.asText();
                }
            }
        }
        return null;
    }
}
