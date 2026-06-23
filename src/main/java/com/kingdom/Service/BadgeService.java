package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.DTO.IN.BadgeIN;
import com.kingdom.Enums.BadgeType;
import com.kingdom.Model.Badge;
import com.kingdom.Model.Kingdom;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Repository.BadgeRepository;
import com.kingdom.Repository.KingdomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BadgeService {
    private final BadgeRepository badgeRepository;
    private final PlayerBadgeService playerBadgeService;
    private final KingdomRepository kingdomRepository;


    public List<Badge> getAllBadges() {
        return badgeRepository.findAll();
    }

    public void addBadge(Integer kingdomId, BadgeIN badgeIN) {
        Kingdom kingdom = kingdomRepository.findKingdomById(kingdomId);
        Badge badge = new Badge();
        badge.setName(badgeIN.getName());
        badge.setDescription(badgeIN.getDescription());
        badge.setType(badgeIN.getType());
        badge.setRequiredValue(badgeIN.getRequiredValue());
        badge.setKingdom(kingdom);
        badgeRepository.save(badge);
    }

    public void updateBadge(Integer id, BadgeIN badgeIN) {
        Badge oldBadge = findBadgeById(id);

        oldBadge.setName(badgeIN.getName());
        oldBadge.setDescription(badgeIN.getDescription());
        oldBadge.setType(badgeIN.getType());
        oldBadge.setRequiredValue(badgeIN.getRequiredValue());

        badgeRepository.save(oldBadge);
    }

    public void deleteBadge(Integer id) {
        Badge badge = findBadgeById(id);

        badgeRepository.delete(badge);
    }

    public Badge findBadgeById(Integer id) {
        Badge badge = badgeRepository.findBadgeById(id);

        if (badge == null) {
            throw new ApiException("Badge not found");
        }

        return badge;
    }

    //
    public void awardBadges(BadgeType type, Integer value, KingdomMembership membership) {
        List<Badge> badges = badgeRepository.findAwardableBadges(type, value, membership);

        for (Badge badge : badges) {
            playerBadgeService.addPlayerBadge(badge.getId(),membership.getId());
        }
    }

    public Kingdom findKingdomById(Integer id) {
        Kingdom kingdom = kingdomRepository.findKingdomById(id);
        if (kingdom == null) {
            throw new ApiException("Kingdom not found");
        }
        return kingdom;
    }

}