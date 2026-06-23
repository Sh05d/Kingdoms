package com.kingdom;

import com.kingdom.Enums.InviteStatus;
import com.kingdom.Enums.LobbyStatus;
import com.kingdom.Enums.LobbyVisibility;
import com.kingdom.Enums.UserRole;
import com.kingdom.Model.Lobby;
import com.kingdom.Model.LobbyInvite;
import com.kingdom.Model.Player;
import com.kingdom.Model.User;
import com.kingdom.Repository.LobbyInviteRepository;
import com.kingdom.Repository.LobbyRepository;
import com.kingdom.Repository.PlayerRepository;
import com.kingdom.Repository.UserRepository;
import org.assertj.core.api.Assertions;
import org.h2.engine.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDateTime;
import java.util.List;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class LobbyServiceTest {

    @Autowired
    private LobbyInviteRepository lobbyInviteRepository;

    @Autowired
    private LobbyRepository lobbyRepository;

    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private UserRepository userRepository;

    Lobby lobby;
    Player player;
    LobbyInvite invite1, invite2;
    List<LobbyInvite> inviteList;

    @BeforeEach
    void setUp() {
        lobbyInviteRepository.deleteAll();
        lobbyRepository.deleteAll();
        playerRepository.deleteAll();
        User user = new User();
        user.setUsername("maysun");
        user.setEmail("maysun@test.com");
        user.setPasswordHash("12345678");
        user.setPhoneNumber("0500000000");
        user.setRole(UserRole.USER);

        userRepository.save(user);

        player = new Player();
        player.setUser(user);
        player.setDisplayName("Maysun");

        playerRepository.save(player);

//        player = new Player();
//        player.setDisplayName("Maysun");
//        playerRepository.save(player);

        lobby = new Lobby();
        lobby.setName("Private Lobby");
        lobby.setDescription("Test private lobby");
        lobby.setVisibility(LobbyVisibility.PRIVATE);
        lobby.setStatus(LobbyStatus.OPEN);
        lobby.setHostPlayerId(1);
        lobby.setStartsAt(LocalDateTime.now().plusDays(1));
        lobby.setEndsAt(LocalDateTime.now().plusDays(2));
        lobbyRepository.save(lobby);

        invite1 = new LobbyInvite();
        invite1.setLobby(lobby);
        invite1.setInvitedPlayer(player);
        invite1.setStatus(InviteStatus.PENDING);
        invite1.setInviteCode("ABC123");
        invite1.setSentAt(LocalDateTime.now());

        invite2 = new LobbyInvite();
        invite2.setLobby(lobby);
        invite2.setInvitedPlayer(player);
        invite2.setStatus(InviteStatus.REJECTED);
        invite2.setInviteCode("XYZ789");
        invite2.setSentAt(LocalDateTime.now());

        lobbyInviteRepository.save(invite1);
        lobbyInviteRepository.save(invite2);
    }

    @Test
    public void deleteInvite() {
        lobbyInviteRepository.delete(invite1);
        LobbyInvite deleted = lobbyInviteRepository.findLobbyInviteById(invite1.getId());
        Assertions.assertThat(deleted).isNull();
    }

    @Test
    public void findLobbyInviteById() {
        LobbyInvite found = lobbyInviteRepository.findLobbyInviteById(invite1.getId());
        Assertions.assertThat(found).isEqualTo(invite1);
    }

}