package com.kingdom.Controller;

import com.kingdom.API.ApiResponse;
import com.kingdom.Config.CustomUserDetails;
import com.kingdom.Service.APIService.LemonSqueezyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final LemonSqueezyService lemonSqueezyService;
    @PostMapping("/checkout/{plan}")
    public ResponseEntity<?> createCheckout(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable String plan
    ) {
        return ResponseEntity.ok(
                lemonSqueezyService.createCheckout(me.getId(), plan)
        );
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestHeader HttpHeaders headers, @RequestBody String rawBody) {
        lemonSqueezyService.processWebhook(headers, rawBody);
        return ResponseEntity.ok(new ApiResponse("Webhook processed successfully"));
    }

    @PostMapping("/renew/{plan}")
    public ResponseEntity<?> renewSubscription(@AuthenticationPrincipal CustomUserDetails me, @PathVariable String plan) {
        return ResponseEntity.ok(lemonSqueezyService.renewSubscription(me.getId(), plan));
    }

    @GetMapping("/plans")
    public ResponseEntity<?> getPlans() {
        return ResponseEntity.ok(lemonSqueezyService.getAvailablePlans());
    }
}
