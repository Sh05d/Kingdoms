package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.DTO.IN.PlayerIn;
import com.kingdom.DTO.IN.WakatimeConnectIn;
import com.kingdom.DTO.OUT.PlayerKingdom;
import com.kingdom.DTO.OUT.PlayerSummary;
import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Enums.UserRole;
import com.kingdom.Model.*;
import com.kingdom.Repository.*;
import com.kingdom.Service.AiService.PlayerAiReportService;
import com.kingdom.Service.APIService.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.kingdom.DTO.OUT.PlayerOut;
import org.modelmapper.ModelMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final ModelMapper modelMapper;
    private final PlayerRepository playerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final LobbyRepository lobbyRepository;
    private final UserRepository userRepository;
    private final KingdomMembershipRepository kingdomMembershipRepository;
    private final PlayerAiReportService playerAiReportService;
    private final EmailService emailService;
    private final ChallengeProgressRepository challengeProgressRepository;

    public PlayerOut getMyProfile(Integer playerId) {
        Player player = checkPlayer(playerId);
        return toPlayerOut(player);
    }

    public void addPlayer(Integer userId, PlayerIn playerIn) {
        User user = userRepository.findUserById(userId);
        if (user == null) {
            throw new ApiException("User not found");
        }
        if (playerRepository.findPlayerById(userId) != null) {
            throw new ApiException("This user already has a player profile");
        }
        user.setRole(UserRole.PLAYER);
        userRepository.save(user);

        Player player = new Player();
        player.setUser(user);
        player.setDisplayName(playerIn.getDisplayName());
        player.setJoinedAt(LocalDateTime.now());
        playerRepository.save(player);
    }

    public void updateMyProfile(Integer playerId, PlayerIn playerIn) {
        Player player = checkPlayer(playerId);

        player.setDisplayName(playerIn.getDisplayName());

        playerRepository.save(player);
    }

    public void deleteMyProfile(Integer playerId) {
        Player player = checkPlayer(playerId);

        Subscription subscription = subscriptionRepository.findSubscriptionById(playerId);
        if (subscription != null)
            subscriptionRepository.delete(subscription);

        List<Lobby> hostedLobbies = lobbyRepository.findAllByHostPlayerId(playerId);
        lobbyRepository.deleteAll(hostedLobbies);

        playerRepository.delete(player);
    }

    //helper method
    public Player checkPlayer(Integer id) {
        Player player = playerRepository.findPlayerById(id);
        if (player == null) {
            throw new ApiException("Player not found");
        }
        return player;
    }

    //shahad endpoint
    public void playerAiReport(Integer playerId) {

        Player player = checkPlayer(playerId);

        if (player.getUser() == null || player.getUser().getEmail() == null || player.getUser().getEmail().isBlank()) {
            throw new ApiException("Player email not found");
        }

        List<KingdomMembership> memberships = kingdomMembershipRepository.findByPlayer(player);

        if (memberships.isEmpty()) {
            throw new ApiException("Player is not joined to any kingdom");
        }

        PlayerAiReportService.PlayerReport report = playerAiReportService.generatePlayerReport(player, memberships);

        emailService.sendPlayerAiReport(player, report.pdf(), report.reportHtml());
    }

    public PlayerSummary playerSummary(Integer playerId) {

        Player player = checkPlayer(playerId);

        List<KingdomMembership> memberships = kingdomMembershipRepository.findByPlayer(player);

        int totalXp = memberships.stream().mapToInt(KingdomMembership::getTotalXP).sum();

        int joinedKingdoms = memberships.size();

        int completedChallenges = challengeProgressRepository.completedChallenges(playerId);

        return new PlayerSummary(totalXp, completedChallenges, joinedKingdoms);
    }

    public PlayerKingdom bestKingdom(Integer playerId) {

        Player player = checkPlayer(playerId);

        List<KingdomMembership> memberships = kingdomMembershipRepository.findByPlayer(player);

        if (memberships.isEmpty()) {
            throw new ApiException("Player has not joined any kingdom");
        }

        KingdomMembership best = memberships.get(0);

        for (KingdomMembership membership : memberships) {

            if (membership.getTotalXP() > best.getTotalXP()) {
                best = membership;
            }
        }

        return new PlayerKingdom(best.getKingdom().getName(), best.getDivision());
    }

    public List<PlayerKingdom> playerKingdoms(Integer playerId) {

        Player player = checkPlayer(playerId);

        List<KingdomMembership> memberships = kingdomMembershipRepository.findByPlayer(player);

        List<PlayerKingdom> result = new ArrayList<>();

        for (KingdomMembership membership : memberships) {
            result.add(new PlayerKingdom(membership.getKingdom().getName(), membership.getDivision()));
        }

        return result;
    }

    private PlayerOut toPlayerOut(Player player) {
        PlayerOut out = modelMapper.map(player, PlayerOut.class);
        if (player.getUser() != null) {
            out.setUsername(player.getUser().getUsername());
            out.setEmail(player.getUser().getEmail());
            out.setPhoneNumber(player.getUser().getPhoneNumber());
        }
        return out;
    }

    public String highestStreak(Integer playerId) {

        Player player = checkPlayer(playerId);

        List<KingdomMembership> memberships =
                kingdomMembershipRepository.findByPlayer(player);

        int highestStreak = 0;

        for (KingdomMembership membership : memberships) {

            if (membership.getStreak() > highestStreak) {
                highestStreak = membership.getStreak();
            }
        }

        return String.valueOf(highestStreak);
    }

    // Maysun's WakaTime connect (grafted from her branch during the merge)
    public void connectWakatime(Integer playerId, WakatimeConnectIn wakatimeConnectIn) {
        Player player = checkPlayer(playerId);
        player.setWakatimeApiKey(wakatimeConnectIn.getWakatimeApiKey());
        playerRepository.save(player);
    }
}
