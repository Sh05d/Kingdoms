package com.kingdom.ServiceTest;

import com.kingdom.API.ApiException;
import com.kingdom.Model.Kingdom;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Model.Player;
import com.kingdom.Repository.ChallengeProgressRepository;
import com.kingdom.Repository.KingdomMembershipRepository;
import com.kingdom.Repository.KingdomRepository;
import com.kingdom.Repository.PlayerRepository;
import com.kingdom.Service.KingdomMembershipService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class KingdomMembershipServiceTest {
    @InjectMocks
    KingdomMembershipService kingdomMembershipService;

    @Mock
    KingdomMembershipRepository kingdomMembershipRepository;

    @Mock
    KingdomRepository kingdomRepository;

    @Mock
    PlayerRepository playerRepository;

    Player player;
    Kingdom kingdom;

    @BeforeEach
    void setUp() {
        player = new Player();
        player.setId(1);
        player.setDisplayName("Shahad");

        kingdom = new Kingdom();
        kingdom.setId(1);
        kingdom.setName("Reading Kingdom");
        kingdom.setDescription("Reading Kingdom Description");
    }

    @Test
    public void joinToKingdomTest() {
        when(playerRepository.findPlayerById(player.getId())).thenReturn(player);
        when(kingdomRepository.findKingdomById(kingdom.getId())).thenReturn(kingdom);
        when(kingdomMembershipRepository.findByPlayerAndKingdom(player, kingdom)).thenReturn(null);
        when(kingdomMembershipRepository.findByPlayer(player)).thenReturn(new ArrayList<>());

        kingdomMembershipService.joinToKingdom(player.getId(), kingdom.getId());

        verify(kingdomMembershipRepository, times(1)).save(any(KingdomMembership.class));
    }
}
