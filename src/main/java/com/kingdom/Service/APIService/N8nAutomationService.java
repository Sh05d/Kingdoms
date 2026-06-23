package com.kingdom.Service.APIService;

import com.kingdom.Enums.LobbyStatus;
import com.kingdom.Enums.SubscriptionStatus;
import com.kingdom.Model.*;
import com.kingdom.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.time.LocalDateTime;
import java.util.*;
@Service
@RequiredArgsConstructor
public class N8nAutomationService {

    private final PlayerRepository playerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final LobbyRepository lobbyRepository;
    private final LobbyMemberRepository lobbyMemberRepository;
    private final KingdomMembershipRepository kingdomMembershipRepository;
    private final ChallengeProgressRepository challengeProgressRepository;

    public List<Map<String, Object>> getWeeklyReports() {
        List<Map<String, Object>> result = new ArrayList<>();

        for (KingdomMembership membership : kingdomMembershipRepository.findAll()) {
            Player player = membership.getPlayer();

            if (player == null || player.getUser() == null) {
                continue;
            }
            Integer xp = membership.getTotalXP() == null ? 0 : membership.getTotalXP();
            List<ChallengeProgress> completed = challengeProgressRepository.findAllByKingdomMembershipAndStatus(membership, com.kingdom.Enums.ProgressStatus.VERIFIED);
            Map<String, Object> data = buildBasePlayerData(player);
            String kingdomName = membership.getKingdom() != null ? membership.getKingdom().getName() : "مملكتك";
            data.put("kingdomName", kingdomName);
            data.put("xp", xp);
            data.put("completedChallenges", completed.size());
            data.put("subject", "تقريرك الأسبوعي في الممالك 👑");
            data.put("message", "مرحباً " + player.getDisplayName() + " 👑\nتقريرك في " + kingdomName + ": "
                    + xp + " نقطة خبرة، وأكملت " + completed.size() + " تحدٍ. واصل الإنجاز!");

            result.add(data);
        }

        return result;
    }

    public List<Map<String, Object>> getExpiringSubscriptions() {
        List<Map<String, Object>> result = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime afterFiveDays = now.plusDays(5);

        for (Subscription subscription : subscriptionRepository.findAll()) {
            if (subscription.getPlayer() == null || subscription.getPlayer().getUser() == null) {
                continue;
            }

            if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
                continue;
            }

            if (subscription.getExpiresAt() == null) {
                continue;
            }

            // 5 days or fewer left -> notify.
            boolean expiresWithinFiveDays = !subscription.getExpiresAt().isBefore(now) && !subscription.getExpiresAt().isAfter(afterFiveDays);

            if (!expiresWithinFiveDays) {
                continue;
            }

            // Count whole calendar days to the expiry DATE (not 24h chunks) so "3 days minus a few hours" reads as 3.
            long daysLeft = Math.max(0, ChronoUnit.DAYS.between(now.toLocalDate(), subscription.getExpiresAt().toLocalDate()));
            Map<String, Object> data = buildBasePlayerData(subscription.getPlayer());
            data.put("daysLeft", daysLeft);
            data.put("plan", subscription.getPlan());
            data.put("expiresAt", subscription.getExpiresAt());
            data.put("subject", "اشتراكك في الممالك على وشك الانتهاء ⏳");
            data.put("message", "مرحباً " + subscription.getPlayer().getDisplayName() + "، اشتراكك ("
                    + subscription.getPlan() + ") ينتهي خلال " + daysLeft + " يوم. جدّده حتى لا تفقد مزاياك 👑");

            result.add(data);
        }

        return result;
    }

    public List<Map<String, Object>> getLobbyWinners() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Lobby lobby : lobbyRepository.findAll()) {
            if (lobby.getStatus() != LobbyStatus.FINISHED || lobby.getWinnerPlayerId() == null) {
                continue;
            }
            Player winner = playerRepository.findPlayerById(lobby.getWinnerPlayerId());
            String winnerName = winner != null ? winner.getDisplayName() : "الفائز";
            List<LobbyMember> members = lobbyMemberRepository.findAllByLobbyId(lobby.getId());
            for (LobbyMember member : members) {
                Player player = member.getPlayer();
                if (player == null || player.getUser() == null) {
                    continue;
                }
                boolean isWinner = player.getId().equals(lobby.getWinnerPlayerId());
                String challengeTitle = lobby.getChallenge() != null ? lobby.getChallenge().getTitle() : "";
                Map<String, Object> data = buildBasePlayerData(player);
                data.put("winnerPlayerId", lobby.getWinnerPlayerId());
                data.put("winnerName", winnerName);
                data.put("isWinner", isWinner);
                data.put("lobbyName", lobby.getName());
                data.put("challengeTitle", challengeTitle);
                data.put("subject", "نتيجة لوبي " + lobby.getName());
                data.put("message", isWinner
                        ? "🎉 مبروك " + player.getDisplayName() + "! فزت في لوبي «" + lobby.getName() + "» — " + challengeTitle
                        : "انتهى لوبي «" + lobby.getName() + "» وفاز " + winnerName + ". حظاً أوفر المرة القادمة! 👑");

                result.add(data);
            }
        }

        return result;
    }

    public List<Map<String, Object>> getChurnRiskPlayers() {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Player player : playerRepository.findAll()) {
            if (player.getUser() == null) {
                continue;
            }

            Map<String, Object> data = buildBasePlayerData(player);
            data.put("subject", "اشتقنا لك في الممالك 👑");
            data.put("message", "مرحباً " + player.getDisplayName()
                    + " 👑 اشتقنا لك! ارجع وأكمل تحدياً جديداً اليوم وواصل تقدّمك.");
            result.add(data);
        }

        return result;
    }

    private Map<String, Object> buildBasePlayerData(Player player) {
        Map<String, Object> map = new HashMap<>();

        map.put("playerId", player.getId());
        map.put("playerName", player.getDisplayName());
        map.put("phone", formatSaudiPhone(player.getUser().getPhoneNumber()));
        map.put("email", player.getUser().getEmail());

        return map;
    }

    private String formatSaudiPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }

        phone = phone.trim().replace(" ", "");

        if (phone.startsWith("+")) {
            return phone;
        }

        if (phone.startsWith("05")) {
            return "+966" + phone.substring(1);
        }

        if (phone.startsWith("5")) {
            return "+966" + phone;
        }

        return phone;
    }
}