package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.Config.AuthUtil;
import com.kingdom.DTO.OUT.LobbyMemberOut;
import com.kingdom.Enums.InviteStatus;
import com.kingdom.Enums.LobbyStatus;
import com.kingdom.Enums.LobbyVisibility;
import com.kingdom.Enums.MemberRole;
import com.kingdom.Model.*;
import com.kingdom.Repository.KingdomMembershipRepository;
import com.kingdom.Repository.LobbyInviteRepository;
import com.kingdom.Repository.LobbyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LobbyMemberService {

   private final ModelMapper modelMapper;
   private final LobbyMemberRepository lobbyMemberRepository;
   private final LobbyInviteRepository lobbyInviteRepository;
   private final KingdomMembershipRepository kingdomMembershipRepository;
   private final LobbyService lobbyService;
   private final PlayerService playerService;
   private final WhatsAppService whatsAppService;

   public List<LobbyMemberOut> getAllLobbyMembers() {
      List<LobbyMemberOut> members = new ArrayList<>();
      for (LobbyMember member : lobbyMemberRepository.findAll()) {
         members.add(toMemberOut(member));
      }
      return members;
   }

   public LobbyMemberOut getLobbyMemberById(Integer memberId) {
      LobbyMember member = lobbyMemberRepository.findLobbyMemberById(memberId);
      if (member == null) {
         throw new ApiException("Lobby member not found");
      }
      return toMemberOut(member);
   }

   public void updateLobbyMemberRole(Integer memberId, MemberRole role) {
      LobbyMember member = lobbyMemberRepository.findLobbyMemberById(memberId);
      if (member == null) {
         throw new ApiException("Lobby member not found");
      }
      member.setRole(role);
      lobbyMemberRepository.save(member);
   }

   public void deleteLobbyMember(Integer memberId) {
      LobbyMember member = lobbyMemberRepository.findLobbyMemberById(memberId);
      if (member == null) {
         throw new ApiException("Lobby member not found");
      }
      lobbyMemberRepository.delete(member);
   }

   public void joinPrivateLobbyByInviteCode(String inviteCode, Integer playerId) {
      Player player = playerService.checkPlayer(playerId);

      LobbyInvite invite = lobbyInviteRepository.findByInviteCode(inviteCode);

      if (invite == null) {
         throw new ApiException("Invalid invite code");
      }

      if (!invite.getInvitedPlayer().getId().equals(playerId)) {
         throw new ApiException("This invite code does not belong to this player");
      }

      if (invite.getStatus() != InviteStatus.PENDING) {
         throw new ApiException("This invite was already used or responded to");
      }

      Lobby lobby = invite.getLobby();

      if (lobby.getVisibility() != LobbyVisibility.PRIVATE) {
         throw new ApiException("This invite code is only for private lobbies");
      }

      if (lobby.getStatus() != LobbyStatus.OPEN) {
         throw new ApiException("Lobby is not open");
      }

      if (lobbyMemberRepository.existsByLobbyIdAndPlayerId(lobby.getId(), playerId)) {
         throw new ApiException("Player already joined this lobby");
      }

      if (lobbyService.isLobbyFull(lobby.getId())) {
         throw new ApiException("This lobby is full");
      }

      LobbyMember member = new LobbyMember();
      member.setLobby(lobby);
      member.setPlayer(player);
      member.setRole(MemberRole.MEMBER);
      member.setJoinedAt(LocalDateTime.now());

      lobbyMemberRepository.save(member);

      invite.setStatus(InviteStatus.ACCEPTED);
      invite.setRespondedAt(LocalDateTime.now());
      lobbyInviteRepository.save(invite);
   }

   public void joinLobby(Integer lobbyId, Integer playerId) {
      Lobby lobby = lobbyService.checkLobby(lobbyId);
      Player player = playerService.checkPlayer(playerId);

      if (lobbyMemberRepository.existsByLobbyIdAndPlayerId(lobbyId, playerId)) {
         throw new ApiException("Player already joined this lobby");
      }
      //make sure 10 members for private and public lobby
      if (lobbyService.isLobbyFull(lobbyId)) {
         throw new ApiException("This lobby is full, the 10-member limit has been reached");
      }
      if (lobby.getVisibility() == LobbyVisibility.PUBLIC) {
         KingdomMembership membership = kingdomMembershipRepository.findByPlayerIdAndKingdomId(playerId, lobby.getKingdom().getId());
         if (membership == null) {
            throw new ApiException("You must join this kingdom first to enter its public lobbies");
         }

         if (!Objects.equals(membership.getDivision(), lobby.getDivision())) {
            throw new ApiException("This public lobby is locked to a different division than yours");
         }
      } else {
         LobbyInvite acceptedInvite = lobbyInviteRepository.findByLobbyIdAndInvitedPlayerIdAndStatus(lobbyId, playerId, InviteStatus.ACCEPTED);
         if (acceptedInvite == null){
            throw new ApiException("You need an accepted invite to join this private lobby");
         }}
      if (lobby.getVisibility() != LobbyVisibility.PUBLIC) {
         throw new ApiException("Private lobbies must be joined using invite code");
      }

      if (lobbyMemberRepository.existsByLobbyIdAndPlayerId(lobbyId, playerId)) {
         throw new ApiException("Player already joined this lobby");
      }

      if (lobbyService.isLobbyFull(lobbyId)) {
         throw new ApiException("This lobby is full, the 10-member limit has been reached");
      }

      KingdomMembership membership =
              kingdomMembershipRepository.findByPlayerIdAndKingdomId(playerId, lobby.getKingdom().getId());

      if (membership == null) {
         throw new ApiException("You must join this kingdom first to enter its public lobbies");
      }

      if (!Objects.equals(membership.getDivision(), lobby.getDivision())) {
         throw new ApiException("This public lobby is locked to a different division than yours");
      }

      LobbyMember member = new LobbyMember();
      member.setLobby(lobby);
      member.setPlayer(player);
      member.setRole(MemberRole.MEMBER);
      member.setJoinedAt(LocalDateTime.now());

      lobbyMemberRepository.save(member);
      notifyJoinedOnWhatsapp(player, lobby);
   }

   // WhatsApp the player the moment they join a (public) lobby, including the lobby's challenge so they know what to do.
   private void notifyJoinedOnWhatsapp(Player player, Lobby lobby) {
      try {
         if (player.getUser() == null || player.getUser().getPhoneNumber() == null
                 || player.getUser().getPhoneNumber().isBlank()) {
            return;
         }
         Challenge challenge = lobby.getChallenge();
         String title = challenge != null ? challenge.getTitle() : "التحدي";
         String desc = (challenge != null && challenge.getDescription() != null) ? challenge.getDescription() : "";
         String msg = "🎉 انضممت إلى اللوبي «" + lobby.getName() + "»!\n"
                 + "تحدّيك: " + title + (desc.isBlank() ? "" : "\n" + desc) + "\n"
                 + "بالتوفيق يا بطل 👑🔥";
         whatsAppService.sendMessage(player.getUser().getPhoneNumber(), msg);
      } catch (Exception ignored) {
      }
   }

   public void leaveLobby(Integer lobbyId, Integer playerId) {
      Lobby lobby = lobbyService.checkLobby(lobbyId);
      LobbyMember member = checkMembership(lobbyId, playerId);
      if (member.getRole() == MemberRole.HOST) {
         throw new ApiException("Host cannot leave the lobby");
      }
      if (lobby.getStatus() != LobbyStatus.OPEN) {
         throw new ApiException("Members can only leave before the lobby starts");
      }
      if (lobbyService.isLockedForExit(lobby)) {
         throw new ApiException("You can't leave, less than 8 hours remain before start, you must complete the challenge");
      }

      if (member.getRole() == MemberRole.HOST) {
         throw new ApiException("Host cannot leave the lobby");
      }

      if (lobby.getStatus() != LobbyStatus.OPEN) {
         throw new ApiException("Members can only leave before the lobby starts");
      }

      if (lobbyService.isLockedForExit(lobby)) {
         throw new ApiException("You can't leave, less than 8 hours remain before start, you must complete the challenge");
      }

      lobbyMemberRepository.delete(member);
   }

   public List<LobbyMemberOut> getMembers(Integer lobbyId) {
      lobbyService.checkLobby(lobbyId);

      List<LobbyMemberOut> members = new ArrayList<>();
      for (LobbyMember m : lobbyMemberRepository.findAllByLobbyId(lobbyId)) {
         members.add(toMemberOut(m));
      }
      return members;
   }

   public void kickMember(Integer lobbyId, Integer hostPlayerId, Integer targetPlayerId) {
      AuthUtil.requireSelfOrAdmin(hostPlayerId);
      Lobby lobby = lobbyService.checkLobby(lobbyId);

      if (!lobby.getHostPlayerId().equals(hostPlayerId)) {
         throw new ApiException("Only the host can kick members");
      }
      if (hostPlayerId.equals(targetPlayerId)) {
         throw new ApiException("Host cannot kick themselves");
      }


      if (hostPlayerId.equals(targetPlayerId)) {
         throw new ApiException("Host cannot kick themselves");
      }

      LobbyMember member = checkMembership(lobbyId, targetPlayerId);

      if (member.getRole() == MemberRole.HOST) {
         throw new ApiException("Host cannot be kicked");
      }

      if (lobby.getStatus() != LobbyStatus.OPEN) {
         throw new ApiException("Members can only be kicked before the lobby starts");
      }

      lobbyMemberRepository.delete(member);
   }

   //helper method
   private LobbyMember checkMembership(Integer lobbyId, Integer playerId) {
      LobbyMember member = lobbyMemberRepository.findByLobbyIdAndPlayerId(lobbyId, playerId);
      if (member == null) {
         throw new ApiException("Player is not a member of this lobby");
      }
      return member;
   }

   private LobbyMemberOut toMemberOut(LobbyMember member) {
      LobbyMemberOut out = modelMapper.map(member, LobbyMemberOut.class);
      if (member.getPlayer() != null) {
         out.setDisplayName(member.getPlayer().getDisplayName());
         if (member.getPlayer().getUser() != null) {
            out.setUsername(member.getPlayer().getUser().getUsername());
         }
      }
      return out;
   }
}
