package com.kingdom.ServiceTest;

import com.kingdom.DTO.OUT.PlayerBadgeOut;
import com.kingdom.Model.Badge;
import com.kingdom.Model.Kingdom;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Model.Player;
import com.kingdom.Model.PlayerBadge;
import com.kingdom.Repository.BadgeRepository;
import com.kingdom.Repository.KingdomMembershipRepository;
import com.kingdom.Repository.KingdomRepository;
import com.kingdom.Repository.PlayerBadgeRepository;
import com.kingdom.Repository.PlayerRepository;
import com.kingdom.Service.PlayerBadgeService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlayerBadgeServiceTest {
    @InjectMocks
    PlayerBadgeService playerBadgeService;

    @Mock
    PlayerBadgeRepository playerBadgeRepository;

    @Mock
    BadgeRepository badgeRepository;

    @Mock
    KingdomMembershipRepository kingdomMembershipRepository;

    @Mock
    PlayerRepository playerRepository;

    @Mock
    KingdomRepository kingdomRepository;

    Player player;
    PlayerBadge playerBadge;

    @BeforeEach
    void setUp() {
        player = new Player();
        player.setId(1);
        player.setDisplayName("Shahad");

        Kingdom kingdom = new Kingdom();
        kingdom.setId(1);
        kingdom.setName("Reading Kingdom");

        Badge badge = new Badge();
        badge.setId(1);
        badge.setName("First XP");
        badge.setDescription("Earned first XP");
        badge.setKingdom(kingdom);

        KingdomMembership membership = new KingdomMembership();
        membership.setId(1);
        membership.setPlayer(player);
        membership.setKingdom(kingdom);

        playerBadge = new PlayerBadge();
        playerBadge.setId(1);
        playerBadge.setBadge(badge);
        playerBadge.setKingdomMembership(membership);
        playerBadge.setEarnedAt(LocalDateTime.now());
    }

    @Test
    public void playerBadgesTest() {
        when(playerRepository.findPlayerById(player.getId())).thenReturn(player);
        when(playerBadgeRepository.findPlayerBadgeByPlayer(player)).thenReturn(List.of(playerBadge));

        List<PlayerBadgeOut> result = playerBadgeService.playerBadges(player.getId());

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals("First XP", result.get(0).getBadgeName());
        Assertions.assertEquals("Reading Kingdom", result.get(0).getKingdomName());

        verify(playerRepository, times(1)).findPlayerById(player.getId());
        verify(playerBadgeRepository, times(1)).findPlayerBadgeByPlayer(player);
    }
}
