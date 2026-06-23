package com.kingdom.Service.APIService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * n8n PUSH: POSTs a JSON payload to the n8n webhook Maysun set up (n8n.webhook.url in application.properties).
 * This is the opposite direction from {@link N8nAutomationService} (which exposes GET feeds n8n PULLS) — here the
 * backend pushes a real-time message INTO an n8n workflow (Webhook-trigger node), which then relays it.
 *
 * Feature-guarded: if n8n.webhook.url is blank the send no-ops and returns false. Best-effort: a failure (n8n
 * down, workflow not listening, bad URL) is swallowed and returns false — it never throws into a business flow.
 */
@Service
public class N8nWebhookClient {

    @Value("${n8n.webhook.url:}")
    private String webhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /** True only when an n8n webhook URL is configured. */
    public boolean isEnabled() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    /** POST a JSON payload to the configured n8n webhook. Returns true if n8n accepted it (2xx). Never throws. */
    public boolean send(Map<String, Object> payload) {
        if (!isEnabled()) {
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response =
                    restTemplate.postForEntity(webhookUrl.trim(), new HttpEntity<>(payload, headers), String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
