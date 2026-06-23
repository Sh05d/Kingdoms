package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.DTO.OUT.PlayerBadgeOut;
import com.kingdom.Model.*;
import com.kingdom.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlayerBadgeService {

    private final PlayerBadgeRepository playerBadgeRepository;
    private final BadgeRepository badgeRepository;
    private final KingdomMembershipRepository kingdomMembershipRepository;
    private final PlayerRepository playerRepository;
    private final KingdomRepository kingdomRepository;


    public List<PlayerBadge> getAllPlayerBadges() {
        return playerBadgeRepository.findAll();
    }

    public void addPlayerBadge(Integer badgeId, Integer membershipId) {

        Badge badge = findBadgeById(badgeId);
        KingdomMembership kingdomMembership = findKingdomMembershipById(membershipId);

        PlayerBadge playerBadge = new PlayerBadge();

        playerBadge.setBadge(badge);
        playerBadge.setKingdomMembership(kingdomMembership);
        playerBadge.setEarnedAt(LocalDateTime.now());

        playerBadgeRepository.save(playerBadge);
    }

    public PlayerBadge getPlayerBadgeById(Integer id) {
        PlayerBadge playerBadge = playerBadgeRepository.findPlayerBadgeById(id);

        if (playerBadge == null) {
            throw new ApiException("Player badge not found");
        }

        return playerBadge;
    }

    public void updatePlayerBadge(Integer id, PlayerBadge playerBadge) {
        PlayerBadge oldPlayerBadge = getPlayerBadgeById(id);

        oldPlayerBadge.setEarnedAt(playerBadge.getEarnedAt());
        oldPlayerBadge.setBadge(playerBadge.getBadge());
        oldPlayerBadge.setKingdomMembership(playerBadge.getKingdomMembership());

        playerBadgeRepository.save(oldPlayerBadge);
    }

    public void deletePlayerBadge(Integer id) {
        PlayerBadge playerBadge = getPlayerBadgeById(id);
        playerBadgeRepository.delete(playerBadge);
    }

    public List<PlayerBadgeOut> playerBadges(Integer playerId){
        Player player = findPlayerById(playerId);
        List<PlayerBadgeOut> playerBadges = new ArrayList<>();

        for(PlayerBadge playerBadge:  playerBadgeRepository.findPlayerBadgeByPlayer(player)){
            playerBadges.add(convertToOut(playerBadge));
        }
        return playerBadges;
    }

    public List<PlayerBadgeOut> earnedKingdomBadges(Integer playerId, Integer kingdomId) {
        Player player = findPlayerById(playerId);
        Kingdom kingdom = findKingdomById(kingdomId);

        List<PlayerBadgeOut> playerBadges = new ArrayList<>();

        for(PlayerBadge playerBadge: playerBadgeRepository.earnedKingdomBadges(player, kingdom)){
            playerBadges.add(convertToOut(playerBadge));
        }
        return playerBadges;
    }

    public PlayerBadgeOut convertToOut(PlayerBadge playerBadge){
        return new PlayerBadgeOut(
                playerBadge.getBadge().getName(),
                playerBadge.getBadge().getDescription(),
                playerBadge.getBadge().getKingdom().getName(),
                playerBadge.getEarnedAt()
        );
    }

    public Badge findBadgeById(Integer id) {

        Badge badge = badgeRepository.findBadgeById(id);

        if (badge == null) {
            throw new ApiException("Badge not found");
        }

        return badge;
    }

    public KingdomMembership findKingdomMembershipById(Integer id) {

        KingdomMembership kingdomMembership =
                kingdomMembershipRepository.findKingdomMembershipById(id);

        if (kingdomMembership == null) {
            throw new ApiException("Kingdom membership not found");
        }

        return kingdomMembership;
    }

    public Player findPlayerById(Integer id) {
        Player player = playerRepository.findPlayerById(id);
        if (player == null) {
            throw new ApiException("Player not found");
        }
        return player;
    }

    public Kingdom findKingdomById(Integer id) {
        Kingdom kingdom = kingdomRepository.findKingdomById(id);
        if (kingdom == null) {
            throw new ApiException("Kingdom not found");
        }
        return kingdom;
    }
}
