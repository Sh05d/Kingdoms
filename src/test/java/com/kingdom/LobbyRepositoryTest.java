package com.kingdom;

import com.kingdom.Enums.LobbyStatus;
import com.kingdom.Enums.LobbyVisibility;
import com.kingdom.Model.Lobby;
import com.kingdom.Repository.LobbyRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.List;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class LobbyRepositoryTest {

    @Autowired
    private LobbyRepository lobbyRepository;

    Lobby lobby1, lobby2, lobby3;
    List<Lobby> lobbyList;

    @BeforeEach
    void setUp() {
        lobbyRepository.deleteAll();

        lobby1 = new Lobby();
        lobby1.setName("Public Lobby");
        lobby1.setDescription("Test public lobby");
        lobby1.setVisibility(LobbyVisibility.PUBLIC);
        lobby1.setStatus(LobbyStatus.OPEN);
        lobby1.setHostPlayerId(1);
        lobby1.setDivision(1);
        lobby1.setStartsAt(LocalDateTime.now().plusDays(1));
        lobby1.setEndsAt(LocalDateTime.now().plusDays(2));

        lobby2 = new Lobby();
        lobby2.setName("Active Lobby");
        lobby2.setDescription("Test active lobby");
        lobby2.setVisibility(LobbyVisibility.PUBLIC);
        lobby2.setStatus(LobbyStatus.ACTIVE);
        lobby2.setHostPlayerId(1);
        lobby2.setDivision(1);
        lobby2.setStartsAt(LocalDateTime.now().minusHours(1));
        lobby2.setEndsAt(LocalDateTime.now().plusHours(2));

        lobby3 = new Lobby();
        lobby3.setName("Finished Lobby");
        lobby3.setDescription("Test finished lobby");
        lobby3.setVisibility(LobbyVisibility.PRIVATE);
        lobby3.setStatus(LobbyStatus.FINISHED);
        lobby3.setHostPlayerId(2);
        lobby3.setDivision(null);
        lobby3.setStartsAt(LocalDateTime.now().minusDays(2));
        lobby3.setEndsAt(LocalDateTime.now().minusDays(1));
        lobby3.setWinnerPlayerId(2);

        lobbyRepository.save(lobby1);
        lobbyRepository.save(lobby2);
        lobbyRepository.save(lobby3);
    }

    @Test
    public void deleteLobby() {
        lobbyRepository.delete(lobby1);
        Lobby deleted = lobbyRepository.findLobbyById(lobby1.getId());
        Assertions.assertThat(deleted).isNull();
    }

    @Test
    public void findLobbyById() {
        Lobby found = lobbyRepository.findLobbyById(lobby1.getId());
        Assertions.assertThat(found).isEqualTo(lobby1);
    }

    @Test
    public void findAllByStatusAndEndsAtBefore() {
        lobbyList = lobbyRepository.findAllByStatusAndEndsAtBefore(LobbyStatus.FINISHED, LocalDateTime.now());
        Assertions.assertThat(lobbyList.size()).isEqualTo(1);
        Assertions.assertThat(lobbyList.get(0).getStatus()).isEqualTo(LobbyStatus.FINISHED);
    }
}