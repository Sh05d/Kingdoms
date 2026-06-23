package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.Config.AuthUtil;
import com.kingdom.DTO.IN.LobbyInviteIN;
import com.kingdom.DTO.OUT.LobbyInviteOut;
import com.kingdom.Enums.InviteStatus;
import com.kingdom.Enums.LobbyStatus;
import com.kingdom.Enums.LobbyVisibility;
import com.kingdom.Model.Challenge;
import com.kingdom.Model.Lobby;
import com.kingdom.Model.LobbyInvite;
import com.kingdom.Model.Player;
import com.kingdom.Model.User;
import com.kingdom.Repository.*;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.kingdom.Enums.InviteStatus.*;

@Service
@RequiredArgsConstructor
public class LobbyInviteService {

    private final ModelMapper modelMapper;
    private final LobbyInviteRepository lobbyInviteRepository;
    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final LobbyService lobbyService;
    private final LobbyMemberRepository lobbyMemberRepository;
    private final LobbyMemberService lobbyMemberService;
    private final WhatsAppService whatsAppService;
    private final com.kingdom.Service.APIService.EmailService emailService;

    // Twilio Quick Reply template (HX...) for the private-lobby invite (قبول / رفض). Blank -> plain-text fallback.
    @Value("${twilio.content.invite-sid:}")
    private String inviteContentSid;

    //BASIC CRUD
    @Transactional
    public void sendInvite(Integer lobbyId, Integer hostPlayerId, String username) {
        AuthUtil.requireSelfOrAdmin(hostPlayerId);
        Lobby lobby = lobbyService.checkLobby(lobbyId);

        if (!lobby.getHostPlayerId().equals(hostPlayerId))
            throw new ApiException("Only the host can send invites for this lobby");

        if (lobby.getVisibility() != LobbyVisibility.PRIVATE)
            throw new ApiException("Invites are only used for private lobbies");

        if (lobby.getStatus() != LobbyStatus.OPEN)
            throw new ApiException("Invites can only be sent before the lobby starts");

        User invitedUser = userRepository.findUserByUsername(username);
        if (invitedUser == null) throw new ApiException("No user found with this username");

        Player invitedPlayer = checkPlayer(invitedUser.getId());

        if (lobbyMemberRepository.existsByLobbyIdAndPlayerId(lobbyId, invitedPlayer.getId()))
            throw new ApiException("This player is already a member of this lobby");

        if (lobbyInviteRepository.findByLobbyIdAndInvitedPlayerIdAndStatus(lobbyId, invitedPlayer.getId(), InviteStatus.PENDING) != null) {
            throw new ApiException("This player already has a pending invite");
        }
        LobbyInvite invite = new LobbyInvite();
        invite.setLobby(lobby);
        invite.setInvitedPlayer(invitedPlayer);
        invite.setStatus(InviteStatus.PENDING);
        invite.setSentAt(LocalDateTime.now());
        // Every invite needs a code: it's how acceptance (WhatsApp قبول tap or /join-private) actually adds the
        // member. Without it the accept failed with "Invalid invite code" and the invite stayed PENDING forever.
        invite.setInviteCode(java.util.UUID.randomUUID().toString());

        lobbyInviteRepository.save(invite);

        // Notify the invited player on WhatsApp with قبول / رفض buttons (the inbound webhook handles the tap).
        notifyInviteOnWhatsapp(invitedUser.getPhoneNumber(), lobby);
        // Also send the invite by email (best-effort).
        notifyInviteOnEmail(invitedUser.getEmail(), invitedPlayer.getDisplayName(), lobby);
    }

    // Send the private-lobby invite over WhatsApp as a Quick Reply (قبول/رفض). Falls back to plain text.
    private void notifyInviteOnWhatsapp(String phone, Lobby lobby) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        String text = buildInviteText(lobby);
        boolean sent = whatsAppService.sendContentTemplate(phone, inviteContentSid, Map.of("1", text));
        if (!sent) {
            // No template configured / Twilio off -> plain-text invite so the flow still works.
            whatsAppService.sendMessage(phone, text + "\n\nردّ بـ: قبول  أو  رفض");
        }
    }

    // Also email the invite (you asked for WhatsApp + email). Best-effort; never blocks the invite.
    private void notifyInviteOnEmail(String email, String invitedName, Lobby lobby) {
        if (email == null || email.isBlank()) {
            return;
        }
        try {
            Challenge challenge = lobby.getChallenge();
            String kingdomName = (challenge != null && challenge.getKingdom() != null && challenge.getKingdom().getName() != null)
                    ? challenge.getKingdom().getName() : "مملكة";
            String challengeDesc = (challenge == null) ? "تحدٍ جديد"
                    : (challenge.getDescription() != null && !challenge.getDescription().isBlank()
                        ? challenge.getDescription() : challenge.getTitle());
            Player host = checkPlayer(lobby.getHostPlayerId());
            String hostName = (host != null && host.getDisplayName() != null && !host.getDisplayName().isBlank())
                    ? host.getDisplayName() : "المضيف";
            String startDate = "—", startTime = "—";
            if (lobby.getStartsAt() != null) {
                startDate = lobby.getStartsAt().toLocalDate().toString();
                startTime = String.format("%02d:%02d", lobby.getStartsAt().getHour(), lobby.getStartsAt().getMinute());
            }
            emailService.sendLobbyInvite(email,
                    (invitedName != null && !invitedName.isBlank()) ? invitedName : "بطل",
                    hostName, lobby.getName(), kingdomName, challengeDesc, startDate, startTime);
        } catch (Exception ignored) {
        }
    }

    /**
     * Handle a WhatsApp قبول/رفض tap. Finds the sender's pending invite by phone; ACCEPT -> accept + join the
     * lobby + send the challenge hype; DECLINE -> just reject (nothing else happens).
     */
    @Transactional
    public String handleWhatsappInviteResponse(String rawPhone, boolean accepted) {
        String phone = whatsAppService.normalizeToE164(rawPhone);
        User user = userRepository.findUserByPhoneNumber(phone);
        if (user == null || user.getPlayer() == null) {
            return "no player for phone";
        }
        Player player = user.getPlayer();

        List<LobbyInvite> pending =
                lobbyInviteRepository.findAllByInvitedPlayerIdAndStatus(player.getId(), PENDING);
        if (pending.isEmpty()) {
            whatsAppService.sendMessage(phone, "لا توجد دعوة معلّقة لك حالياً 👑");
            return "no pending invite";
        }

        LobbyInvite invite = pending.get(0);
        Lobby lobby = invite.getLobby();

        if (accepted) {
            // joinPrivateLobbyByInviteCode adds the member AND flips the invite to ACCEPTED in one transaction.
            // (Do NOT call joinLobby here — it rejects every private lobby, which rolled the accept back -> stuck PENDING.)
            lobbyMemberService.joinPrivateLobbyByInviteCode(invite.getInviteCode(), player.getId());
            whatsAppService.sendMessage(phone, buildHype(lobby));
            return "accepted";
        }
        rejectInvite(invite.getId(), player.getId());
        return "declined";
    }

    private String buildInviteText(Lobby lobby) {
        Challenge challenge = lobby.getChallenge();
        String challengeLine = challenge != null
                ? "التحدي: " + challenge.getTitle()
                + (challenge.getDescription() != null ? " — " + challenge.getDescription() : "")
                : "التحدي: سيُعلن قريباً";
        return "👑 لديك دعوة للوبي «" + lobby.getName() + "».\n"
                + (lobby.getDescription() != null ? lobby.getDescription() + "\n" : "")
                + challengeLine + "\nهل تقبل الانضمام؟";
    }

    private String buildHype(Lobby lobby) {
        Challenge challenge = lobby.getChallenge();
        String title = challenge != null ? challenge.getTitle() : "التحدي";
        String desc = challenge != null && challenge.getDescription() != null ? challenge.getDescription() : "";
        return "🎉 انضممت إلى اللوبي «" + lobby.getName() + "»!\n"
                + "تحدّيك: " + title + "\n"
                + desc + "\n"
                + "بالتوفيق يا بطل 👑🔥";
    }

    public void acceptInvite(Integer inviteId, Integer playerId) {
        LobbyInvite invite = checkInvite(inviteId);
        validateOwnership(invite, playerId);

        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setRespondedAt(LocalDateTime.now());
        lobbyInviteRepository.save(invite);
    }

    public void rejectInvite(Integer inviteId, Integer playerId) {
        LobbyInvite invite = checkInvite(inviteId);
        validateOwnership(invite, playerId);

        invite.setStatus(InviteStatus.REJECTED);
        invite.setRespondedAt(LocalDateTime.now());
        lobbyInviteRepository.save(invite);
    }

    public List<LobbyInviteOut> getMyPendingInvites(Integer playerId) {
        List<LobbyInviteOut> invites = new ArrayList<>();
        for (LobbyInvite i : lobbyInviteRepository.findAllByInvitedPlayerIdAndStatus(playerId, InviteStatus.PENDING)) {
            invites.add(toInviteOut(i));
        }
        return invites;
    }

    // --- Invite CRUD (used by LobbyInviteController). sendInvite(Integer) wraps the username-based WhatsApp invite. ---
    public List<LobbyInviteOut> getAllInvites() {
        List<LobbyInviteOut> invites = new ArrayList<>();
        for (LobbyInvite i : lobbyInviteRepository.findAll()) {
            invites.add(toInviteOut(i));
        }
        return invites;
    }

    public LobbyInviteOut getInviteById(Integer inviteId) {
        return toInviteOut(checkInvite(inviteId));
    }

    public void updateInviteStatus(Integer inviteId, InviteStatus status) {
        LobbyInvite invite = checkInvite(inviteId);
        invite.setStatus(status);
        if (status == InviteStatus.ACCEPTED || status == InviteStatus.REJECTED) {
            invite.setRespondedAt(LocalDateTime.now());
        }
        lobbyInviteRepository.save(invite);
    }

    public void deleteInvite(Integer inviteId) {
        lobbyInviteRepository.delete(checkInvite(inviteId));
    }

    @Transactional
    public void sendInvite(Integer lobbyId, Integer hostPlayerId, Integer invitedPlayerId) {
        AuthUtil.requireSelfOrAdmin(hostPlayerId);
        Player invitedPlayer = checkPlayer(invitedPlayerId);
        User invitedUser = invitedPlayer.getUser();
        if (invitedUser == null) {
            throw new ApiException("Invited player has no user account");
        }
        sendInvite(lobbyId, hostPlayerId, invitedUser.getUsername());
    }

    public void resendInvite(Integer inviteId) {
        LobbyInvite invite = checkInvite(inviteId);
        AuthUtil.requireSelfOrAdmin(invite.getLobby().getHostPlayerId());
        User invitedUser = invite.getInvitedPlayer().getUser();
        if (invitedUser != null) {
            notifyInviteOnWhatsapp(invitedUser.getPhoneNumber(), invite.getLobby());
            notifyInviteOnEmail(invitedUser.getEmail(), invite.getInvitedPlayer().getDisplayName(), invite.getLobby());
        }
    }


    //HELPER METHODS
    private void validateOwnership(LobbyInvite invite, Integer playerId) {
        if (!invite.getInvitedPlayer().getId().equals(playerId))
            throw new ApiException("This invite does not belong to this player");

        if (invite.getStatus() != InviteStatus.PENDING)
            throw new ApiException("This invite was already responded to");
    }

    private LobbyInvite checkInvite(Integer id) {
        LobbyInvite invite = lobbyInviteRepository.findLobbyInviteById(id);
        if (invite == null) throw new ApiException("Invite not found"); //check invite

        return invite;
    }

    private Player checkPlayer(Integer id) {
        Player player = playerRepository.findPlayerById(id);
        if (player == null) throw new ApiException("Player not found"); //check player

        return player;
    }

    //عشان الريسبونس يكون مرتب اكثر
    private LobbyInviteOut toInviteOut(LobbyInvite invite) {
        LobbyInviteOut out = modelMapper.map(invite, LobbyInviteOut.class);
        out.setLobbyId(invite.getLobby().getId());
        out.setLobbyName(invite.getLobby().getName());
        out.setInvitedPlayerId(invite.getInvitedPlayer().getId());
        return out;
    }
}