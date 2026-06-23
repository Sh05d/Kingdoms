package com.kingdom.Repository;

import com.kingdom.Model.LobbyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LobbyMemberRepository extends JpaRepository<LobbyMember, Integer> {
    List<LobbyMember> findAllByLobbyId(Integer lobbyId);

    LobbyMember findLobbyMemberById(Integer id);
    LobbyMember findByLobbyIdAndPlayerId(Integer lobbyId, Integer playerId);
    boolean existsByLobbyIdAndPlayerId(Integer lobbyId, Integer playerId);
    //as a validations 10 members
    int countByLobbyId(Integer lobbyId);

    List<LobbyMember> findByPlayerId(Integer playerId);

    List<LobbyMember> findAllByPlayerId(Integer playerId);
}
