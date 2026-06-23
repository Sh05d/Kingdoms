package com.kingdom.Repository;

import com.kingdom.Enums.InviteStatus;
import com.kingdom.Enums.LobbyVisibility;
import com.kingdom.Model.Lobby;
import com.kingdom.Model.LobbyInvite;
import com.kingdom.Model.LobbyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LobbyInviteRepository extends JpaRepository<LobbyInvite, Integer> {

    LobbyInvite findLobbyInviteById(Integer id);

    List<LobbyInvite> findAllByInvitedPlayerIdAndStatus(Integer invitedPlayerId, InviteStatus status);

    LobbyInvite findByLobbyIdAndInvitedPlayerIdAndStatus(Integer lobbyId, Integer invitedPlayerId, InviteStatus status);

    LobbyInvite findByInviteCode(String inviteCode);
}
