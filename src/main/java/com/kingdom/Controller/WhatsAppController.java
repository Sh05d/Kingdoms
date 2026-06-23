package com.kingdom.Controller;

import com.kingdom.Service.ForTestImage.WhatsAppWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/whatsapp")
@RequiredArgsConstructor
public class WhatsAppController {

    private final WhatsAppWebhookService whatsAppWebhookService;

    @PostMapping(value = "/webhook", produces = "application/xml")
    public ResponseEntity<String> receiveWhatsApp(
            @RequestParam("From") String from,
            @RequestParam(value = "Body", required = false) String body,
            @RequestParam(value = "NumMedia", required = false) String numMedia,
            @RequestParam(value = "MediaUrl0", required = false) String mediaUrl,
            @RequestParam(value = "MediaContentType0", required = false) String contentType
    ) {
        whatsAppWebhookService.handleIncomingNutritionImageAsync(
                from, body, numMedia, mediaUrl, contentType
        );

        String twiml = """
                <Response>
                    <Message>استلمت صورتك 👑 جاري تحليل الوجبة...</Message>
                </Response>
                """;

        return ResponseEntity.ok(twiml);
    }
}
