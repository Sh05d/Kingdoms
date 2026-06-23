package com.kingdom.Controller;

import com.kingdom.API.ApiResponse;
import com.kingdom.Config.CustomUserDetails;
import com.kingdom.Model.Subscription;
import com.kingdom.Service.APIService.N8nAutomationService;
import com.kingdom.Service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final N8nAutomationService n8nAutomationService;

    @GetMapping("/getDetails")
    public ResponseEntity<?> getSubscriptionDetails(@AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.status(200).body(subscriptionService.getSubscriptionDetails(me.getId()));
    }
    @PostMapping("/add/{playerId}")
    public ResponseEntity<?> addSubscription(@PathVariable Integer playerId, @RequestBody Subscription subscription) {
        subscriptionService.addSubscription(playerId, subscription);
        return ResponseEntity.status(200).body(new ApiResponse("Subscription added successfully"));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateSubscription(@PathVariable Integer id, @RequestBody @Valid Subscription subscription) {
        subscriptionService.updateSubscription(id, subscription);
        return ResponseEntity.status(200).body(new ApiResponse("Subscription updated successfully"));
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancelMySubscription(@AuthenticationPrincipal CustomUserDetails me) {
        subscriptionService.cancelMySubscription(me.getId());
        return ResponseEntity.status(200).body(new ApiResponse("Subscription cancelled successfully"));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteMySubscription(@AuthenticationPrincipal CustomUserDetails me) {
        subscriptionService.deleteMySubscription(me.getId());
        return ResponseEntity.status(200).body(new ApiResponse("Subscription deleted successfully"));
    }

    //Shahad endpoint
    @GetMapping("/days-left")
    public ResponseEntity<?> subscriptionDaysLeft(@AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(new ApiResponse(subscriptionService.subscriptionDaysLeft(me.getId())));
    }

    //for admin
    @GetMapping("/premium-active")
    public ResponseEntity<?> getAllActivePremiumSubscribers() {
        return ResponseEntity.ok(subscriptionService.getAllActivePremiumSubscribers());
    }

    @GetMapping("/subscriptions-expiring")
    public ResponseEntity<?> getExpiringSubscriptions() {
        return ResponseEntity.ok(n8nAutomationService.getExpiringSubscriptions());
    }

    // The authenticated player's OWN premium status (the /premium-active endpoint above lists ALL subscribers = admin).
    @GetMapping("/am-i-premium")
    public ResponseEntity<?> amIPremium(@AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(java.util.Map.of("premium", subscriptionService.isPlayerPremium(me.getId())));
    }

    // Push the player's subscription-expiry reminder into the n8n workflow (relays to WhatsApp/email).
    @PostMapping("/notify-expiry")
    public ResponseEntity<?> notifyExpiry(@AuthenticationPrincipal CustomUserDetails me) {
        boolean sent = subscriptionService.notifySubscriptionExpiry(me.getId());
        return ResponseEntity.ok(new ApiResponse(sent
                ? "تم إرسال إشعار قرب انتهاء الاشتراك عبر n8n ✅"
                : "تعذّر الإرسال — تأكد أن n8n يعمل وأن n8n.webhook.url مضبوط."));
    }

    // DEMO helper: set your own subscription to expire in N days so the expiry notification shows a real countdown.
    @PostMapping("/demo-set-days/{days}")
    public ResponseEntity<?> demoSetDays(@AuthenticationPrincipal CustomUserDetails me, @PathVariable int days) {
        return ResponseEntity.ok(new ApiResponse(subscriptionService.setExpiryInDays(me.getId(), days)));
    }
}
