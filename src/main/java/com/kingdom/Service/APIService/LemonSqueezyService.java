package com.kingdom.Service.APIService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingdom.API.ApiException;
import com.kingdom.Enums.SubscriptionPlan;
import com.kingdom.Enums.SubscriptionStatus;
import com.kingdom.Model.Player;
import com.kingdom.Model.Subscription;
import com.kingdom.Repository.PlayerRepository;
import com.kingdom.Repository.SubscriptionRepository;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LemonSqueezyService {

    private final WebClient webClient;
    private final PlayerRepository playerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LemonSqueezyService(
            @Qualifier("lemonWebClient") WebClient webClient,
            PlayerRepository playerRepository,
            SubscriptionRepository subscriptionRepository
    ) {
        this.webClient = webClient;
        this.playerRepository = playerRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Value("${lemon.squeezy.store.id}")
    private String storeId;

    @Value("${lemon.squeezy.variant.monthly}")
    private String monthlyVariantId;

    @Value("${lemon.squeezy.variant.annual}")
    private String annualVariantId;

    @Value("${lemon.squeezy.webhook.secret}")
    private String secret;

    public String createCheckout(Integer playerId, String plan) {
        Player player = playerRepository.findPlayerById(playerId);

        if (player == null) {
            throw new ApiException("Player not found");
        }

        String variantId = resolveVariantId(plan);

        String payload = """
                {
                  "data": {
                    "type": "checkouts",
                    "attributes": {
                      "checkout_data": {
                        "custom": {
                          "player_id": "%s"
                        }
                      }
                    },
                    "relationships": {
                      "store": {
                        "data": {
                          "type": "stores",
                          "id": "%s"
                        }
                      },
                      "variant": {
                        "data": {
                          "type": "variants",
                          "id": "%s"
                        }
                      }
                    }
                  }
                }
                """.formatted(playerId, storeId, variantId);

        String response = webClient.post()
                .uri("/checkouts")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return extractCheckoutUrl(response);
    }

    public void processWebhook(HttpHeaders headers, String rawBody) {
        try {
            String signature = headers.getFirst("X-Signature");

            if (signature != null && !signature.isBlank()) {
                verifySignature(rawBody, signature);
            }

            JsonNode root = objectMapper.readTree(rawBody);

            String eventType = headers.getFirst("X-Event-Name");

            if (eventType == null || eventType.isBlank()) {
                eventType = root.path("meta").path("event_name").asText();
            }

            System.out.println("Webhook event: " + eventType);

            switch (eventType) {
                case "subscription_created" -> handleSubscriptionCreatedOrUpdated(root);
                case "subscription_updated" -> handleSubscriptionCreatedOrUpdated(root);
                case "subscription_expired" -> handleSubscriptionExpired(root);
                default -> System.out.println("Unhandled Lemon event: " + eventType);
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new ApiException("Failed to process webhook: " + e.getMessage());
        }
    }
    public List<Map<String, String>> getAvailablePlans() {

        List<Map<String, String>> plans = new ArrayList<>();

        plans.add(Map.of(
                "name", "MONTHLY",
                "plan", "PREMIUM_MONTHLY",
                "variantId", monthlyVariantId));

        plans.add(Map.of(
                "name", "ANNUAL",
                "plan", "PREMIUM_ANNUAL",
                "variantId", annualVariantId));
        return plans;
    }


    private void handleSubscriptionCreatedOrUpdated(JsonNode root) {
        Integer playerId = extractPlayerId(root);

        Player player = playerRepository.findPlayerById(playerId);

        if (player == null) {
            throw new ApiException("Player not found for Id: " + playerId);
        }

        String lemonSubscriptionId = root.path("data").path("id").asText();
        JsonNode attributes = root.path("data").path("attributes");
        String variantId = attributes.path("variant_id").asText("");
        String status = attributes.path("status").asText("active");
        String renewsAt = attributes.path("renews_at").asText("");

        Subscription subscription = subscriptionRepository.findSubscriptionById(playerId);

        if (subscription == null) {
            subscription = new Subscription();
            subscription.setPlayer(player);
            subscription.setStartDate(LocalDateTime.now());
        }

        subscription.setLemonSubscriptionId(lemonSubscriptionId);
        subscription.setPlan(determinePlanByVariantId(variantId));
        subscription.setStatus(mapStatus(status));
        subscription.setExpiresAt(parseExpiresAt(renewsAt, subscription.getPlan()));

        subscriptionRepository.save(subscription);
    }

    private void handleSubscriptionExpired(JsonNode root) {
        Integer playerId = extractPlayerId(root);

        Subscription subscription = subscriptionRepository.findSubscriptionById(playerId);

        if (subscription == null) {
            throw new ApiException("Subscription not found for player Id: " + playerId);
        }

        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscription.setAutoRenew(false);

        subscriptionRepository.save(subscription);
    }

    private Integer extractPlayerId(JsonNode root) {
        JsonNode playerIdNode = root.path("meta").path("custom_data").path("player_id");

        if (playerIdNode.isMissingNode() || playerIdNode.asText().isBlank() || playerIdNode.asInt() == 0) {
            throw new ApiException("Missing player_id in Lemon Squeezy webhook custom_data");
        }

        return Integer.valueOf(playerIdNode.asText());
    }

    private String resolveVariantId(String plan) {
        if ("MONTHLY".equalsIgnoreCase(plan)) {
            return monthlyVariantId;
        }

        if ("ANNUAL".equalsIgnoreCase(plan)) {
            return annualVariantId;
        }

        throw new ApiException("Invalid plan: must be MONTHLY or ANNUAL");
    }

    private String extractCheckoutUrl(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String url = root.path("data").path("attributes").path("url").asText();

            if (url == null || url.isBlank()) {
                throw new ApiException("Checkout URL not found in Lemon response");
            }

            return url;

        } catch (Exception e) {
            throw new ApiException("Failed to extract checkout URL: " + e.getMessage());
        }
    }

    private void verifySignature(String rawBody, String signature) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );

            sha256Hmac.init(secretKey);

            String computedSignature = Hex.encodeHexString(
                    sha256Hmac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8))
            );

            if (!computedSignature.equals(signature)) {
                throw new ApiException("Invalid Lemon Squeezy webhook signature");
            }

        } catch (Exception e) {
            throw new ApiException("Signature verification failed: " + e.getMessage());
        }
    }

    private SubscriptionPlan determinePlan(String variantName) {
        if (variantName == null) {
            return SubscriptionPlan.PREMIUM_MONTHLY;
        }

        if (variantName.toLowerCase().contains("annual")
                || variantName.contains("سنوي")) {
            return SubscriptionPlan.PREMIUM_ANNUAL;
        }

        return SubscriptionPlan.PREMIUM_MONTHLY;
    }

    private SubscriptionStatus mapStatus(String lemonStatus) {
        return switch (lemonStatus) {
            case "active", "on_trial" -> SubscriptionStatus.ACTIVE;
            case "cancelled" -> SubscriptionStatus.CANCELLED;
            case "expired" -> SubscriptionStatus.EXPIRED;
            case "past_due", "unpaid" -> SubscriptionStatus.PAST_DUE;
            default -> SubscriptionStatus.ACTIVE;
        };
    }

    private LocalDateTime parseExpiresAt(String renewsAt, SubscriptionPlan plan) {
        if (renewsAt != null && !renewsAt.isBlank() && !"null".equalsIgnoreCase(renewsAt)) {
            return LocalDateTime.parse(renewsAt.substring(0, 19));
        }
        if (plan == SubscriptionPlan.PREMIUM_ANNUAL) {
            return LocalDateTime.now().plusYears(1);
        }
        return LocalDateTime.now().plusMonths(1);
    }
    //تجديد اشتراك اذا كان هو مشترك
    public String renewSubscription(Integer playerId, String plan) {
        Subscription subscription = subscriptionRepository.findSubscriptionById(playerId);
        if (subscription == null) {
            throw new ApiException("No existing subscription found to renew");
        }
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE && subscription.getExpiresAt() != null && subscription.getExpiresAt().isAfter(LocalDateTime.now().plusDays(7))) {
            throw new ApiException("Subscription is still active and not close to expiration");
        }

        return createCheckout(playerId, plan);
    }
    private SubscriptionPlan determinePlanByVariantId(String variantId) {
        if (variantId.equals(monthlyVariantId)) {
            return SubscriptionPlan.PREMIUM_MONTHLY;
        }

        if (variantId.equals(annualVariantId)) {
            return SubscriptionPlan.PREMIUM_ANNUAL;
        }

        throw new ApiException("Unknown Lemon Squeezy variant id: " + variantId);
    }
}