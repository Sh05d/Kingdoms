package com.kingdom.Repository;

import com.kingdom.Enums.Division;
import com.kingdom.Enums.LobbyStatus;
import com.kingdom.Enums.LobbyVisibility;
import com.kingdom.Model.Lobby;
import com.kingdom.Model.LobbyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LobbyRepository extends JpaRepository<Lobby, Integer> {

    Lobby findLobbyById(Integer id);

    List<Lobby> findAllByHostPlayerId(Integer hostPlayerId);

    List<Lobby> findAllByVisibilityAndKingdomIdAndDivision(LobbyVisibility visibility, Integer kingdomId, Integer division);

    List<Lobby> findByStatusAndStartsAtBefore(LobbyStatus status, LocalDateTime startsAt);
    List<Lobby> findAllByStatusAndEndsAtBefore(LobbyStatus status, LocalDateTime endsAt);
    List<Lobby> findAllByVisibilityAndKingdomId(LobbyVisibility visibility, Integer kingdomId);
    List<Lobby> findByStatusAndEndsAtBefore(LobbyStatus status, LocalDateTime time);

    // Lobbies built on a given challenge — used to auto-resolve a lobby when a member finishes its challenge.
    List<Lobby> findAllByChallenge_Id(Integer challengeId);

    List<Lobby> findAllByKingdomIdAndVisibility(Integer kingdomId, LobbyVisibility visibility);
}
