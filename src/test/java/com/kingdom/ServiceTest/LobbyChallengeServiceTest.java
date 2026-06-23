package com.kingdom.ServiceTest;

import com.kingdom.Enums.LobbyStatus;
import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Model.Challenge;
import com.kingdom.Model.ChallengeProgress;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Model.Lobby;
import com.kingdom.Model.LobbyMember;
import com.kingdom.Model.Player;
import com.kingdom.Model.User;
import com.kingdom.Repository.ChallengeProgressRepository;
import com.kingdom.Repository.ChallengeQuestionRepository;
import com.kingdom.Repository.LobbyMemberRepository;
import com.kingdom.Repository.LobbyRepository;
import com.kingdom.Service.LobbyChallengeService;
import com.kingdom.Service.WhatsAppService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LobbyChallengeServiceTest {

    @InjectMocks
    LobbyChallengeService service;

    @Mock
    LobbyRepository lobbyRepository;
    @Mock
    LobbyMemberRepository lobbyMemberRepository;
    @Mock
    ChallengeProgressRepository challengeProgressRepository;
    @Mock
    ChallengeQuestionRepository challengeQuestionRepository;
    @Mock
    WhatsAppService whatsAppService;

    // ---- helpers ----------------------------------------------------------

    private Player player(int id, String name) {
        User u = new User();
        u.setId(id);
        u.setPhoneNumber("+96650000000" + id);
        Player p = new Player();
        p.setId(id);
        p.setDisplayName(name);
        p.setUser(u);
        return p;
    }

    private LobbyMember member(Lobby lobby, Player player) {
        LobbyMember m = new LobbyMember();
        m.setLobby(lobby);
        m.setPlayer(player);
        return m;
    }

    private ChallengeProgress verifiedProgress(Player player, int verifiedValue) {
        KingdomMembership km = new KingdomMembership();
        km.setPlayer(player);
        ChallengeProgress cp = new ChallengeProgress();
        cp.setStatus(ProgressStatus.VERIFIED);
        cp.setVerifiedValue(verifiedValue);
        cp.setKingdomMembership(km);
        return cp;
    }

    // ---- resolveWinner ----------------------------------------------------

    @Test
    void resolveWinner_crownsHigherVerifiedValue_andFinishesLobbyWithWinnerSet() {
        Player p1 = player(1, "Alpha"); // donates / scores 500
        Player p2 = player(2, "Beta");  // donates / scores 300

        Challenge challenge = new Challenge();
        challenge.setId(100);
        challenge.setVerificationSource("NEOTEK_OPEN_BANKING"); // CHARITY -> highest verifiedValue wins

        Lobby lobby = new Lobby();
        lobby.setId(7);
        lobby.setName("Donation Race");
        lobby.setStatus(LobbyStatus.OPEN);
        lobby.setChallenge(challenge);
        lobby.setLobbyMembers(Set.of(member(lobby, p1), member(lobby, p2)));

        when(lobbyRepository.findById(7)).thenReturn(Optional.of(lobby));
        when(challengeProgressRepository.findAllByChallenge_IdAndStatus(100, ProgressStatus.VERIFIED))
                .thenReturn(List.of(verifiedProgress(p1, 500), verifiedProgress(p2, 300)));

        Integer winnerId = service.resolveWinner(7);

        // Higher verifiedValue (500 -> player 1) wins.
        assertEquals(1, winnerId);
        // Lobby is FINISHED (not deleted) with the winner recorded.
        assertEquals(LobbyStatus.FINISHED, lobby.getStatus());
        assertNotNull(lobby.getWinnerPlayerId());
        assertEquals(1, lobby.getWinnerPlayerId());
        // The finish was persisted.
        verify(lobbyRepository).save(lobby);
        // Winner is hyped over WhatsApp (members are also notified, so >= 1 send).
        verify(whatsAppService, atLeastOnce()).sendMessage(any(), any());
    }
}
