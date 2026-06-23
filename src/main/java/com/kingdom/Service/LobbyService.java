package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.Config.AuthUtil;
import com.kingdom.DTO.IN.LobbyIN;
import com.kingdom.DTO.OUT.LobbyOut;
import com.kingdom.DTO.OUT.FinishedLobby;
import com.kingdom.Enums.LobbyStatus;
import com.kingdom.Enums.LobbyVisibility;
import com.kingdom.Enums.MemberRole;
import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Model.*;
import com.kingdom.Repository.*;
import com.kingdom.Service.APIService.EmailService;
import com.kingdom.Service.AiService.OpenAiClient;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LobbyService {

    private final ModelMapper modelMapper;
    private final LobbyRepository lobbyRepository;
    private final ChallengeRepository challengeRepository;
    private final KingdomRepository kingdomRepository;
    private final KingdomMembershipRepository kingdomMembershipRepository;
    private final SubscriptionService subscriptionService;
    private final LobbyMemberRepository lobbyMemberRepository;
    private final PlayerService playerService;
    private final ChallengeProgressRepository challengeProgressRepository;
    private final OpenAiClient openAiClient;
    private final WhatsAppService whatsAppService;
    private final EmailService emailService;

    private static final long LOCK_HOURS_BEFORE_START = 8;
    public static final int MAX_LOBBY_MEMBERS = 10;

    public List<LobbyOut> getAllLobbies() {
        List<LobbyOut> result = new ArrayList<>();

        for (Lobby lobby : lobbyRepository.findAll()) {
            result.add(toLobbyOut(lobby));
        }
        return result;
    }

    public void updateLobby(Integer lobbyId, LobbyIN lobbyIn) {
        Lobby lobby = checkLobby(lobbyId);

        if (lobby.getStatus() != LobbyStatus.OPEN) {
            throw new ApiException("Only open lobbies can be updated");
        }

        lobby.setName(lobbyIn.getName());
        lobby.setVisibility(lobbyIn.getVisibility());
        lobby.setStartsAt(lobbyIn.getStartsAt());

        if (lobby.getChallenge() != null) {
            lobby.setEndsAt(computeEndsAt(lobbyIn.getStartsAt(), lobby.getChallenge()));
        }

        lobbyRepository.save(lobby);
    }

    public void deleteLobby(Integer lobbyId) {
        Lobby lobby = checkLobby(lobbyId);
        lobbyRepository.delete(lobby);
    }
    public Integer createLobby(Integer kingdomId, Integer challengeId, Integer hostPlayerId, LobbyIN lobbyIn) {
        AuthUtil.requireSelfOrAdmin(hostPlayerId);

        if (!subscriptionService.isPlayerPremium(hostPlayerId)) {
            throw new ApiException("Creating a lobby requires an active Premium subscription");
        }

        Kingdom kingdom = checkKingdom(kingdomId);
        Challenge challenge = checkChallenge(challengeId);
        Player host = playerService.checkPlayer(hostPlayerId);

        KingdomMembership hostMembership =
                kingdomMembershipRepository.findByPlayerIdAndKingdomId(hostPlayerId, kingdomId);

        if (hostMembership == null) {
            throw new ApiException("You must join this kingdom before hosting a lobby in it");
        }

        Lobby lobby = modelMapper.map(lobbyIn, Lobby.class);

        lobby.setId(null);
        lobby.setHostPlayerId(hostPlayerId);
        lobby.setKingdom(kingdom);
        lobby.setChallenge(challenge);
        lobby.setStatus(LobbyStatus.OPEN);
        lobby.setEndsAt(computeEndsAt(lobbyIn.getStartsAt(), challenge));

        if (lobbyIn.getVisibility() == LobbyVisibility.PUBLIC) {
            lobby.setDivision(hostMembership.getDivision());
        } else {
            lobby.setDivision(null);
        }

        Lobby savedLobby = lobbyRepository.save(lobby);

        LobbyMember hostMember = new LobbyMember();
        hostMember.setLobby(savedLobby);
        hostMember.setPlayer(host);
        hostMember.setRole(MemberRole.HOST);
        hostMember.setJoinedAt(LocalDateTime.now());

        lobbyMemberRepository.save(hostMember);
        return savedLobby.getId();
    }

    //end cred

    public void cancelLobby(Integer lobbyId, Integer hostPlayerId) {
        AuthUtil.requireSelfOrAdmin(hostPlayerId);
        Lobby lobby = checkLobby(lobbyId);

        if (!lobby.getHostPlayerId().equals(hostPlayerId)) {
            throw new ApiException("Only the host can cancel this lobby");
        }
        if (lobby.getStatus() != LobbyStatus.OPEN) {
            throw new ApiException("Only open lobbies can be cancelled");
        }
        if (isLockedForExit(lobby)) {
            throw new ApiException("You can't cancel this lobby because less than 8 hours remain before start");
        }

        lobby.setStatus(LobbyStatus.CANCELLED);
        notifyLobbyCancelled(lobby, hostPlayerId);
        lobbyRepository.save(lobby);
    }
    public List<LobbyOut> getMyPrivateLobbies(Integer kingdomId, Integer playerId) {

        playerService.checkPlayer(playerId);
        checkKingdom(kingdomId);

        List<LobbyOut> lobbies = new ArrayList<>();

        for (LobbyMember member : lobbyMemberRepository.findAllByPlayerId(playerId)) {

            Lobby lobby = member.getLobby();

            if (lobby.getKingdom().getId().equals(kingdomId)
                    && lobby.getVisibility() == LobbyVisibility.PRIVATE) {

                lobbies.add(toLobbyOut(lobby));
            }
        }

        return lobbies;
    }
    public List<LobbyOut> getPublicLobbiesByKingdom(Integer kingdomId, Integer requesterPlayerId) {
        KingdomMembership membership = kingdomMembershipRepository.findByPlayerIdAndKingdomId(requesterPlayerId, kingdomId);
        if (membership == null) {
            throw new ApiException("You must join this kingdom first to see its public lobbies");
        }
        List<LobbyOut> result = new ArrayList<>();
        for (Lobby l : lobbyRepository.findAllByVisibilityAndKingdomIdAndDivision(LobbyVisibility.PUBLIC, kingdomId, membership.getDivision())) {
            result.add(toLobbyOut(l));
        }
        return result;
    }

    public LobbyOut getLobbyById(Integer lobbyId) {
        return toLobbyOut(checkLobby(lobbyId));
    }

    public List<LobbyOut> getAvailableLobbies(Integer playerId) {
        List<LobbyOut> result = new ArrayList<>();

        for (KingdomMembership membership : kingdomMembershipRepository.findAllByPlayerId(playerId)) {
            List<Lobby> lobbies = lobbyRepository.findAllByVisibilityAndKingdomIdAndDivision(
                    LobbyVisibility.PUBLIC, membership.getKingdom().getId(), membership.getDivision());

            for (Lobby lobby : lobbies) {
                if (lobby.getStatus() == LobbyStatus.OPEN && !lobbyMemberRepository.existsByLobbyIdAndPlayerId(lobby.getId(), playerId) && !isLobbyFull(lobby.getId())) {result.add(toLobbyOut(lobby));
                }
            }
        }

        return result;
    }


    public boolean isLobbyFull(Integer lobbyId) {
        return lobbyMemberRepository.countByLobbyId(lobbyId) >= MAX_LOBBY_MEMBERS;
    }

    public Lobby checkLobby(Integer id) {
        Lobby lobby = lobbyRepository.findLobbyById(id);
        if (lobby == null) {
            throw new ApiException("Lobby not found");
        }
        return lobby;
    }

    @Scheduled(fixedRate = 60000)
    public void autoFinishLobbies() {

        List<Lobby> dueLobbies = lobbyRepository.findAllByStatusAndEndsAtBefore(LobbyStatus.ACTIVE, LocalDateTime.now());
        List<Lobby> updatedLobbies = new ArrayList<>();

        for (Lobby lobby : dueLobbies) {
            try {
                Integer winnerId = determineLobbyWinner(lobby);
                lobby.setWinnerPlayerId(winnerId);
                lobby.setStatus(LobbyStatus.FINISHED);
            } catch (ApiException e) {
                lobby.setStatus(LobbyStatus.EXPIRED);
            }
            updatedLobbies.add(lobby);
        }

        lobbyRepository.saveAll(updatedLobbies);
    }

    public void finishLobby(Integer lobbyId, Integer hostPlayerId, Integer winnerPlayerId) {
        AuthUtil.requireSelfOrAdmin(hostPlayerId);
        Lobby lobby = checkLobby(lobbyId);
        if (!lobby.getHostPlayerId().equals(hostPlayerId)) {
            throw new ApiException("Only the host can finish this lobby");
        }
        if (lobby.getStatus() != LobbyStatus.ACTIVE) {
            throw new ApiException("Only an active lobby can be finished");
        }

        Integer finalWinnerId;
        try {
            finalWinnerId = determineLobbyWinner(lobby);
        } catch (ApiException e) {
            if (winnerPlayerId == null) {
                throw new ApiException("Could not determine a winner automatically — winnerPlayerId is required");
            }
            if (!lobbyMemberRepository.existsByLobbyIdAndPlayerId(lobbyId, winnerPlayerId)) {
                throw new ApiException("Winner must be a member of this lobby");
            }
            finalWinnerId = winnerPlayerId;
        }

        lobby.setStatus(LobbyStatus.FINISHED);
        lobby.setWinnerPlayerId(finalWinnerId);
        lobbyRepository.save(lobby);
        notifyLobbyFinished(lobby, hostPlayerId, finalWinnerId);
    }
    public List<LobbyOut> getMyLobbies(Integer playerId) {
        List<LobbyOut> result = new ArrayList<>();

        for (LobbyMember member : lobbyMemberRepository.findAllByPlayerId(playerId)) {
            result.add(toLobbyOut(member.getLobby()));
        }

        return result;
    }

    public Map<String, Object> getLobbyHost(Integer lobbyId) {
        Lobby lobby = checkLobby(lobbyId);
        Player host = playerService.checkPlayer(lobby.getHostPlayerId());
        Map<String, Object> hostData = new HashMap<>();
        hostData.put("hostPlayerId", host.getId());
        hostData.put("displayName", host.getDisplayName());

        if (host.getUser() != null) {
            hostData.put("username", host.getUser().getUsername());
            hostData.put("email", host.getUser().getEmail());
        }

        return hostData;
    }

    public String suggestLobbyForPlayer(Integer playerId) {
        List<KingdomMembership> memberships = kingdomMembershipRepository.findAllByPlayerId(playerId);

        if (memberships.isEmpty()) {
            throw new ApiException("Player is not joined in any kingdom");
        }

        StringBuilder availableLobbies = new StringBuilder();

        for (KingdomMembership membership : memberships) {
            List<Lobby> lobbies = lobbyRepository.findAllByVisibilityAndKingdomIdAndDivision(LobbyVisibility.PUBLIC, membership.getKingdom().getId(), membership.getDivision());

            for (Lobby lobby : lobbies) {
                if (lobby.getStatus() == LobbyStatus.OPEN) {
                    availableLobbies.append("""
                        Lobby ID: %s
                        Lobby Name: %s
                        Kingdom: %s
                        Challenge: %s
                        Starts At: %s
                        
                        """.formatted(lobby.getId(), lobby.getName(), lobby.getKingdom().getName(), lobby.getChallenge().getTitle(), lobby.getStartsAt()));
                }
            }
        }

        if (availableLobbies.isEmpty()) {
            throw new ApiException("No suitable public lobbies found for this player");
        }

        String instructions = """
            أنت مساعد ذكي في منصة الممالك.
            اقترح أفضل لوبي للاعب بناءً على الممالك التي هو مشترك فيها.
            اختر لوبي واحد فقط.
            اشرح السبب باختصار وبالعربية.
            """;

        String input = """
            هذه اللوبيات المناسبة للاعب:
            
            %s
            
            أرجع النتيجة بهذا الشكل:
            {
              "recommendedLobbyId": 0,
              "reason": "string"
            }
            """.formatted(availableLobbies);

        String result = openAiClient.generate(instructions, input);

        if (result == null || result.isBlank()) {
            throw new ApiException("AI recommendation failed");
        }

        return result;
    }

    public void forceDeleteLobby(Integer lobbyId) {
        Lobby lobby = checkLobby(lobbyId);
        lobbyRepository.delete(lobby);
    }

    public List<LobbyOut> getMyActiveLobbies(Integer playerId) {
        List<LobbyOut> result = new ArrayList<>();

        for (LobbyMember member : lobbyMemberRepository.findAllByPlayerId(playerId)) {
            Lobby lobby = member.getLobby();

            if (lobby.getStatus() == LobbyStatus.OPEN || lobby.getStatus() == LobbyStatus.ACTIVE) {result.add(toLobbyOut(lobby));
            }
        }

        return result;
    }

    //helper method
    public boolean isLockedForExit(Lobby lobby) {
        if (lobby.getStartsAt() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (lobby.getStartsAt().isBefore(now)) {
            return true;
        }
        return Duration.between(now, lobby.getStartsAt()).toHours() < LOCK_HOURS_BEFORE_START;
    }
//check
    private Kingdom checkKingdom(Integer id) {
        Kingdom kingdom = kingdomRepository.findKingdomById(id);
        if (kingdom == null) {
            throw new ApiException("Kingdom not found");
        }
        return kingdom;
    }

    private Challenge checkChallenge(Integer id) {
        Challenge challenge = challengeRepository.findChallengeById(id);
        if (challenge == null) {
            throw new ApiException("Challenge not found");
        }
        return challenge;
    }

    private LobbyOut toLobbyOut(Lobby lobby) {
        LobbyOut out = modelMapper.map(lobby, LobbyOut.class);
        if (lobby.getKingdom() != null) {
            out.setKingdomName(lobby.getKingdom().getName());
        }
        if (lobby.getChallenge() != null) {
            out.setChallengeTitle(lobby.getChallenge().getTitle());
        }
        return out;
    }

    private LocalDateTime computeEndsAt(LocalDateTime startsAt, Challenge challenge) {
        return switch (challenge.getPeriod()) {
            case DAILY -> startsAt.plusDays(1);
            case WEEKLY -> startsAt.plusWeeks(1);
            case MONTHLY -> startsAt.plusMonths(1);
            default -> throw new ApiException("Unsupported period for lobby duration: " + challenge.getPeriod());
        };
    }

    private Integer determineLobbyWinner(Lobby lobby) {
        if (lobby.getChallenge() == null) {
            throw new ApiException("Lobby has no challenge");
        }

        String source = lobby.getChallenge().getVerificationSource();

        if ("AI_KNOWLEDGE".equalsIgnoreCase(source)) {
            return determineKnowledgeWinner(lobby);
        }
        if ("GITHUB".equalsIgnoreCase(source) || "FOOD_IMAGE".equalsIgnoreCase(source)) {
            return determineFastestWinner(lobby);
        }

        throw new ApiException("No automatic winner-determination strategy for verification source: " + source);
    }

    private Integer determineKnowledgeWinner(Lobby lobby) {
        List<LobbyMember> members = lobbyMemberRepository.findAllByLobbyId(lobby.getId());

        Integer bestPlayerId = null;
        Integer bestScore = null;

        for (LobbyMember member : members) {
            KingdomMembership membership = kingdomMembershipRepository
                    .findByPlayerIdAndKingdomId(member.getPlayer().getId(), lobby.getKingdom().getId());
            if (membership == null) continue;

            List<ChallengeProgress> progresses = challengeProgressRepository
                    .findAllByKingdomMembershipAndChallenge(membership, lobby.getChallenge());

            for (ChallengeProgress progress : progresses) {
                Integer score = progress.getVerifiedValue();
                if (score == null) continue;

                if (bestScore == null || score > bestScore) {
                    bestScore = score;
                    bestPlayerId = member.getPlayer().getId();
                }
            }
        }

        if (bestPlayerId == null) {
            throw new ApiException("No knowledge challenge results found yet for this lobby's members");
        }

        return bestPlayerId;
    }

    private Integer determineFastestWinner(Lobby lobby) {
        List<LobbyMember> members = lobbyMemberRepository.findAllByLobbyId(lobby.getId());

        Integer fastestPlayerId = null;
        LocalDateTime fastestTime = null;

        for (LobbyMember member : members) {
            KingdomMembership membership = kingdomMembershipRepository.findByPlayerIdAndKingdomId(member.getPlayer().getId(), lobby.getKingdom().getId());
            if (membership == null) continue;

            List<ChallengeProgress> progresses = challengeProgressRepository.findAllByKingdomMembershipAndChallenge(membership, lobby.getChallenge());

            for (ChallengeProgress progress : progresses) {
                if (progress.getStatus() != ProgressStatus.VERIFIED) continue;
                LocalDateTime finishedAt = progress.getFinishedAt();
                if (finishedAt == null) continue;

                if (fastestTime == null || finishedAt.isBefore(fastestTime)) {
                    fastestTime = finishedAt;
                    fastestPlayerId = member.getPlayer().getId();
                }
            }
        }

        if (fastestPlayerId == null) {
            throw new ApiException("No verified challenge submissions found yet for this lobby's members");
        }

        return fastestPlayerId;
    }

    private void notifyLobbyFinished(Lobby lobby, Integer finisherPlayerId, Integer winnerPlayerId) {
        Player finisher = playerService.checkPlayer(finisherPlayerId);
        Player winner = playerService.checkPlayer(winnerPlayerId);

        String message = """
            🏁 تم إنهاء اللوبي

            %s أنهى التحدي في لوبي "%s".

            🏆 الفائز:
            %s

            شكرًا لمشاركتكم في التحدي 👑
            """.formatted(finisher.getDisplayName(), lobby.getName(), winner.getDisplayName());

        notifyLobbyMembers(lobby, "تم إنهاء اللوبي في الممالك 👑", message);
    }

    private void notifyLobbyCancelled(Lobby lobby, Integer hostPlayerId) {
        Player host = playerService.checkPlayer(hostPlayerId);

        String message = """
            ⚠️ تم حذف اللوبي

            %s حذف لوبي "%s" قبل بداية التحدي.

            التحدي لم يعد متاحًا للمشاركة.
            نراك في تحديات قادمة 👑
            """.formatted(host.getDisplayName(), lobby.getName());

        notifyLobbyMembers(lobby, "تم حذف اللوبي في الممالك", message);
    }

    private void notifyLobbyMembers(Lobby lobby, String subject, String message) {
        List<LobbyMember> members = lobbyMemberRepository.findAllByLobbyId(lobby.getId());

        for (LobbyMember member : members) {
            Player player = member.getPlayer();

            if (player == null || player.getUser() == null) {
                continue;
            }

            User user = player.getUser();

            try {
                if (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) {
                    whatsAppService.sendMessage(user.getPhoneNumber(), message);
                }
            } catch (Exception e) {
                System.out.println("Failed to send lobby WhatsApp notification: " + e.getMessage());
            }

            try {
                if (user.getEmail() != null && !user.getEmail().isBlank()) {
                    emailService.send(user.getEmail(), subject, message);
                }
            } catch (Exception e) {
                System.out.println("Failed to send lobby Email notification: " + e.getMessage());
            }
        }
    }

    // Shahad's lobby endpoints (grafted from her branch during the merge)
    public String lobbyMemberCount(Integer lobbyId) {
        Lobby lobby = checkLobby(lobbyId);
        int currentMembers = lobby.getLobbyMembers().size();
        return currentMembers + "/10";
    }

    public List<FinishedLobby> playerFinishedLobbies(Integer playerId) {
        playerService.checkPlayer(playerId);
        List<LobbyMember> memberships = lobbyMemberRepository.findByPlayerId(playerId);
        List<FinishedLobby> result = new ArrayList<>();
        for (LobbyMember member : memberships) {
            Lobby lobby = member.getLobby();
            if (lobby.getStatus() == LobbyStatus.FINISHED) {
                String winnerName = "No winner";
                if (lobby.getWinnerPlayerId() != null) {
                    winnerName = playerService.checkPlayer(lobby.getWinnerPlayerId()).getDisplayName();
                }
                String challengeTitle = "No challenge";
                String challengeDifficulty = "No difficulty";
                if (lobby.getChallenge() != null) {
                    challengeTitle = lobby.getChallenge().getTitle();
                    challengeDifficulty = lobby.getChallenge().getDifficulty().name();
                }
                result.add(new FinishedLobby(lobby.getName(), lobby.getDescription(), challengeTitle, challengeDifficulty, winnerName));
            }
        }
        return result;
    }
    }
