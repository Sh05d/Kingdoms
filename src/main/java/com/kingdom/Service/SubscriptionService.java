package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.DTO.OUT.SubscriptionOut;
import com.kingdom.Enums.SubscriptionPlan;
import com.kingdom.Enums.SubscriptionStatus;
import com.kingdom.Model.Player;
import com.kingdom.Model.Subscription;
import com.kingdom.Repository.PlayerRepository;
import com.kingdom.Repository.SubscriptionRepository;
import com.kingdom.Service.APIService.N8nWebhookClient;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final ModelMapper modelMapper;
    private final SubscriptionRepository subscriptionRepository;
    private final PlayerRepository playerRepository;
    private final N8nWebhookClient n8nWebhookClient;

    public SubscriptionOut getSubscriptionDetails(Integer playerId) {
        Subscription subscription = checkSubscription(playerId);
        return modelMapper.map(subscription, SubscriptionOut.class);
    }
    public void addSubscription(Integer playerId, Subscription subscription) {
        Player player = checkPlayer(playerId);
        subscription.setPlayer(player);
        subscriptionRepository.save(subscription);
    }

    public void updateSubscription(Integer id, Subscription subscription) {
        Subscription oldSubscription = subscriptionRepository.findSubscriptionById(id);
        if (oldSubscription == null) {
            throw new ApiException("Subscription not found");
        }

        oldSubscription.setLemonSubscriptionId(subscription.getLemonSubscriptionId());
        oldSubscription.setStartDate(subscription.getStartDate());
        oldSubscription.setStatus(subscription.getStatus());
        oldSubscription.setExpiresAt(subscription.getExpiresAt());
        oldSubscription.setAutoRenew(subscription.isAutoRenew());
       oldSubscription.setPlan(subscription.getPlan());

        subscriptionRepository.save(oldSubscription);
    }

    public void deleteMySubscription(Integer playerId) {
        Subscription subscription = checkSubscription(playerId);
        subscriptionRepository.delete(subscription);
    }
    //end CRED

    public void cancelMySubscription(Integer playerId) {
        Subscription subscription = checkSubscription(playerId);

        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new ApiException("Subscription is already cancelled");
        }
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setAutoRenew(false);

        subscriptionRepository.save(subscription);
    }

    public List<SubscriptionOut> getAllActivePremiumSubscribers() {
        List<SubscriptionOut> result = new ArrayList<>();

        for (Subscription subscription : subscriptionRepository.findAll()) {
            if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
                continue;
            }
            if (subscription.getPlan() != SubscriptionPlan.PREMIUM_MONTHLY && subscription.getPlan() != SubscriptionPlan.PREMIUM_ANNUAL) {
                continue;
            }
            result.add(modelMapper.map(subscription, SubscriptionOut.class));
        }

        return result;
    }

    //helper method
    private Subscription checkSubscription(Integer id) {
        Subscription subscription = subscriptionRepository.findSubscriptionById(id);
        if (subscription == null) {
            throw new ApiException("Subscription not found");
        }
        return subscription;
    }
    private Player checkPlayer(Integer id) {
        Player player = playerRepository.findPlayerById(id);
        if (player == null) {
            throw new ApiException("Player not found");
        }
        return player;
    }

    //check subscription for lobby check
    public boolean isPlayerPremium(Integer playerId) {
        Subscription sub = subscriptionRepository.findSubscriptionById(playerId);
        return sub != null && (sub.getPlan() == SubscriptionPlan.PREMIUM_MONTHLY || sub.getPlan() == SubscriptionPlan.PREMIUM_ANNUAL) && sub.getStatus() == SubscriptionStatus.ACTIVE;
    }

    // Shahad endpoint
    public String subscriptionDaysLeft(Integer playerId) {

        Subscription subscription =
                subscriptionRepository.findSubscriptionById(playerId);

        if (subscription == null) {
            throw new ApiException("Subscription not found");
        }

        if (subscription.getExpiresAt() == null) {
            throw new ApiException("Subscription has no expiration date");
        }

        // Whole calendar days to the expiry DATE, so a sub expiring in ~3 days reads as "3", not "2".
        long daysLeft = ChronoUnit.DAYS.between(
                LocalDate.now(),
                subscription.getExpiresAt().toLocalDate()
        );

        return Math.max(0, daysLeft) + " يوم متبقي";
    }

    // PUSH the player's subscription-expiry reminder into the n8n workflow (which relays it to WhatsApp/email).
    public boolean notifySubscriptionExpiry(Integer playerId) {
        Subscription sub = subscriptionRepository.findSubscriptionById(playerId);
        if (sub == null || sub.getPlayer() == null || sub.getPlayer().getUser() == null) {
            throw new ApiException("Subscription not found");
        }
        long daysLeft = sub.getExpiresAt() == null ? 0
                : Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), sub.getExpiresAt().toLocalDate()));
        String name = sub.getPlayer().getDisplayName();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "subscription_expiry");
        payload.put("phone", sub.getPlayer().getUser().getPhoneNumber());
        payload.put("email", sub.getPlayer().getUser().getEmail());
        payload.put("name", name);
        payload.put("daysLeft", daysLeft);
        payload.put("plan", sub.getPlan() == null ? "" : sub.getPlan().toString());
        payload.put("subject", "اشتراكك في الممالك ⏳");
        payload.put("message", "مرحباً " + name + "، اشتراكك (" + sub.getPlan() + ") ينتهي خلال "
                + daysLeft + " يوم. جدّده حتى لا تفقد مزاياك 👑");
        return n8nWebhookClient.send(payload);
    }

    // DEMO helper: set the player's own subscription to expire in N days, so the expiry notification has a real
    // countdown to show. Restrict or remove before final submission.
    public String setExpiryInDays(Integer playerId, int days) {
        Subscription sub = subscriptionRepository.findSubscriptionById(playerId);
        if (sub == null) {
            throw new ApiException("No subscription found — subscribe first.");
        }
        sub.setExpiresAt(LocalDateTime.now().plusDays(days));
        subscriptionRepository.save(sub);
        return "تم ضبط اشتراكك لينتهي خلال " + days + " يوم.";
    }
}
