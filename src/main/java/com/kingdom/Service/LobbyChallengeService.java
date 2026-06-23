package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.Enums.LobbyStatus;
import com.kingdom.Enums.ProgressStatus;
import com.kingdom.Model.ChallengeProgress;
import com.kingdom.Model.Lobby;
import com.kingdom.Model.LobbyMember;
import com.kingdom.Model.Player;
import com.kingdom.Repository.ChallengeProgressRepository;
import com.kingdom.Repository.ChallengeQuestionRepository;
import com.kingdom.Repository.LobbyMemberRepository;
import com.kingdom.Repository.LobbyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// =================================================================================================
// Lobby challenge resolution (Anas). Lobby challenges award NO XP — competition only.
//
//   Resolution depends on the challenge's verification source (3 archetypes):
//     • QUIZ    (WHATSAPP: Knowledge/Faith/Reading): winner = MOST correct, ties → earliest finish.
//               Resolves on a PERFECT score, OR all members finished, OR the deadline.
//     • CHARITY (NEOTEK_OPEN_BANKING): a "highest donor" race. Donations are hand-entered per player
//               (verifiedValue = SAR), then the host clicks resolve -> highest total wins. MANUAL only.
//     • FIRST   (everything else — Strava/Steam/AI-PDF...): FIRST member to verify the task wins, instantly.
//
//   On resolve the lobby is set FINISHED (NOT deleted) + winnerPlayerId; winner is hyped, the rest told
//   who won + "good luck next", a late finisher is told the lobby already has a winner.
// =================================================================================================
@Service
@RequiredArgsConstructor
public class LobbyChallengeService {

    private final LobbyRepository lobbyRepository;
    private final LobbyMemberRepository lobbyMemberRepository;
    private final ChallengeProgressRepository challengeProgressRepository;
    private final ChallengeQuestionRepository challengeQuestionRepository;
    private final WhatsAppService whatsAppService;

    private enum ResolveType { QUIZ, CHARITY, FIRST }

    private ResolveType resolveType(String source) {
        if ("WHATSAPP".equalsIgnoreCase(source)) {
            return ResolveType.QUIZ;
        }
        if ("NEOTEK_OPEN_BANKING".equalsIgnoreCase(source)) {
            return ResolveType.CHARITY;
        }
        return ResolveType.FIRST;
    }

    /** Is this challenge a LOBBY challenge for this player? Used to skip XP — only solo (kingdom) challenges pay. */
    public boolean isLobbyChallenge(Integer challengeId, Integer playerId) {
        try {
            if (challengeId == null || playerId == null) {
                return false;
            }
            for (Lobby lobby : lobbyRepository.findAllByChallenge_Id(challengeId)) {
                if (lobbyMemberRepository.existsByLobbyIdAndPlayerId(lobby.getId(), playerId)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * AUTO-resolve, called after a member completes a lobby's challenge. Branches on the challenge type:
     * QUIZ -> resolve by score when aced/all-finished; CHARITY -> wait for the manual resolve; FIRST -> this
     * member wins instantly (first to verify). Best-effort: never breaks the finish.
     */
    @Transactional
    public void onChallengeVerified(ChallengeProgress progress) {
        try {
            if (progress == null || progress.getChallenge() == null
                    || progress.getKingdomMembership() == null
                    || progress.getKingdomMembership().getPlayer() == null) {
                return;
            }
            Player player = progress.getKingdomMembership().getPlayer();
            Integer challengeId = progress.getChallenge().getId();
            ResolveType type = resolveType(progress.getChallenge().getVerificationSource());

            for (Lobby lobby : lobbyRepository.findAllByChallenge_Id(challengeId)) {
                if (!isMember(lobby, player.getId())) {
                    continue;
                }
                LobbyStatus st = lobby.getStatus();
                if (st == LobbyStatus.FINISHED) {
                    if (lobby.getWinnerPlayerId() != null && !lobby.getWinnerPlayerId().equals(player.getId())) {
                        notifyAlreadyHasWinner(lobby, player);
                    }
                    continue;
                }
                if (st != LobbyStatus.OPEN && st != LobbyStatus.ACTIVE) {
                    continue;
                }

                switch (type) {
                    case QUIZ -> maybeResolveByScore(lobby, progress, player);
                    case CHARITY -> send(player, "تم تسجيل مشاركتك في لوبي التبرّع 🤲\n"
                            + "الفائز هو الأعلى تبرّعاً عند إغلاق اللوبي.");
                    case FIRST -> markLobbyFinished(lobby, player,
                            winnerDesc(ResolveType.FIRST, 0, 0, lobby));
                }
            }
        } catch (Exception e) {
            // best-effort: never break the finish
        }
    }

    // QUIZ resolve: rank by score; resolve now if someone aced it or all members finished, else record + wait.
    private void maybeResolveByScore(Lobby lobby, ChallengeProgress justFinished, Player justPlayer) {
        Integer challengeId = lobby.getChallenge().getId();
        int total = questionCount(challengeId);

        Leader leader = leader(lobby, challengeId);
        boolean someoneAced = total > 0 && leader.bestScore >= total;
        boolean allFinished = !leader.memberIds.isEmpty() && leader.finishedIds.size() >= leader.memberIds.size();

        if ((someoneAced || allFinished) && leader.winner != null) {
            markLobbyFinished(lobby, leader.winner, winnerDesc(ResolveType.QUIZ, leader.bestScore, total, lobby));
        } else {
            int myScore = justFinished.getVerifiedValue() == null ? 0 : justFinished.getVerifiedValue();
            send(justPlayer, "تم تسجيل نتيجتك: " + myScore + " من " + total
                    + " ⏳\nفي انتظار بقية المتنافسين لإعلان الفائز...");
        }
    }

    /**
     * MANUAL resolve (POST /lobby-challenge/resolve/{lobbyId}): rank members by verifiedValue and crown the
     * winner now. For QUIZ that's most-correct; for CHARITY that's highest donation; ties -> earliest finish.
     * Sets the lobby FINISHED (not deleted). Returns the winner id, or null if nobody has a recorded result.
     */
    @Transactional
    public Integer resolveWinner(Integer lobbyId) {
        Lobby lobby = lobbyRepository.findById(lobbyId).orElse(null);
        if (lobby == null) {
            throw new ApiException("Lobby not found");
        }
        if (lobby.getChallenge() == null) {
            throw new ApiException("Lobby has no challenge");
        }
        if (lobby.getStatus() == LobbyStatus.FINISHED) {
            return lobby.getWinnerPlayerId();
        }
        Integer challengeId = lobby.getChallenge().getId();
        Leader leader = leader(lobby, challengeId);
        if (leader.winner == null) {
            return null;
        }
        ResolveType type = resolveType(lobby.getChallenge().getVerificationSource());
        int total = (type == ResolveType.QUIZ) ? questionCount(challengeId) : 0;
        markLobbyFinished(lobby, leader.winner, winnerDesc(type, leader.bestScore, total, lobby));
        return leader.winner.getId();
    }

    // DEADLINE trigger: OPEN lobbies whose endsAt has passed get ranked (or EXPIRED if nobody finished).
    @Scheduled(fixedRate = 120000)
    @Transactional
    public void resolveDueOpenLobbies() {
        try {
            for (Lobby lobby : lobbyRepository.findAllByStatusAndEndsAtBefore(LobbyStatus.OPEN, LocalDateTime.now())) {
                try {
                    Integer winner = resolveWinner(lobby.getId());
                    if (winner == null) {
                        lobby.setStatus(LobbyStatus.EXPIRED);
                        lobbyRepository.save(lobby);
                    }
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            // best-effort
        }
    }

    // Rank the lobby members who finished this challenge: highest verifiedValue, ties -> earliest finish.
    private Leader leader(Lobby lobby, Integer challengeId) {
        Leader r = new Leader();
        r.memberIds = memberPlayerIds(lobby);
        for (ChallengeProgress p :
                challengeProgressRepository.findAllByChallenge_IdAndStatus(challengeId, ProgressStatus.VERIFIED)) {
            if (p.getKingdomMembership() == null || p.getKingdomMembership().getPlayer() == null) {
                continue;
            }
            Integer pid = p.getKingdomMembership().getPlayer().getId();
            if (!r.memberIds.contains(pid)) {
                continue;
            }
            r.finishedIds.add(pid);
            int s = p.getVerifiedValue() == null ? 0 : p.getVerifiedValue();
            LocalDateTime t = p.getFinishedAt();
            boolean better = s > r.bestScore
                    || (s == r.bestScore && t != null && (r.bestTime == null || t.isBefore(r.bestTime)));
            if (r.winner == null || better) {
                r.bestScore = s;
                r.bestTime = t;
                r.winner = p.getKingdomMembership().getPlayer();
            }
        }
        return r;
    }

    private static final class Leader {
        Set<Integer> memberIds = new HashSet<>();
        Set<Integer> finishedIds = new HashSet<>();
        Player winner = null;
        int bestScore = -1;
        LocalDateTime bestTime = null;
    }

    private String winnerDesc(ResolveType type, int score, int total, Lobby lobby) {
        return switch (type) {
            case QUIZ -> "بنتيجة " + Math.max(score, 0) + " من " + total;
            case CHARITY -> "بإجمالي تبرّع " + Math.max(score, 0) + " ريال — الأعلى تبرّعاً";
            case FIRST -> "أول من أنهى التحدي";
        };
    }

    // Set the lobby FINISHED + record the winner, hype the winner, tell the rest who won.
    private void markLobbyFinished(Lobby lobby, Player winner, String winnerDesc) {
        if (lobby.getStatus() == LobbyStatus.FINISHED) {
            return; // idempotent (race / double-trigger guard)
        }
        lobby.setStatus(LobbyStatus.FINISHED);
        lobby.setWinnerPlayerId(winner.getId());
        lobbyRepository.save(lobby);

        String lobbyName = safe(lobby.getName(), "اللوبي");
        String winnerName = safe(winner.getDisplayName(), "اللاعب");

        send(winner, "🏆 مبروك! فزت في لوبي «" + lobbyName + "» — " + winnerDesc + "! 👑🔥");

        if (lobby.getLobbyMembers() != null) {
            for (LobbyMember m : lobby.getLobbyMembers()) {
                if (m.getPlayer() == null || m.getPlayer().getId().equals(winner.getId())) {
                    continue;
                }
                send(m.getPlayer(), "انتهى لوبي «" + lobbyName + "» 🏁\nالفائز: " + winnerName + " (" + winnerDesc + ")"
                        + "\nحظ أوفر في تحديات اللوبي القادمة! 👑");
            }
        }
    }

    private void notifyAlreadyHasWinner(Lobby lobby, Player latePlayer) {
        String lobbyName = safe(lobby.getName(), "اللوبي");
        String winnerName = "اللاعب";
        if (lobby.getWinnerPlayerId() != null && lobby.getLobbyMembers() != null) {
            for (LobbyMember m : lobby.getLobbyMembers()) {
                if (m.getPlayer() != null && m.getPlayer().getId().equals(lobby.getWinnerPlayerId())) {
                    winnerName = safe(m.getPlayer().getDisplayName(), "اللاعب");
                    break;
                }
            }
        }
        send(latePlayer, "لوبي «" + lobbyName + "» لديه فائز بالفعل: " + winnerName
                + " 🏆\nجرّب تحدي لوبي آخر — حظ أوفر! 👑");
    }

    private int questionCount(Integer challengeId) {
        List<?> qs = challengeQuestionRepository.findQuestionsByChallengeId(challengeId);
        return qs == null ? 0 : qs.size();
    }

    private Set<Integer> memberPlayerIds(Lobby lobby) {
        Set<Integer> ids = new HashSet<>();
        if (lobby.getLobbyMembers() != null) {
            for (LobbyMember m : lobby.getLobbyMembers()) {
                if (m.getPlayer() != null) {
                    ids.add(m.getPlayer().getId());
                }
            }
        }
        return ids;
    }

    private boolean isMember(Lobby lobby, Integer playerId) {
        if (lobby.getLobbyMembers() == null) {
            return false;
        }
        for (LobbyMember m : lobby.getLobbyMembers()) {
            if (m.getPlayer() != null && m.getPlayer().getId().equals(playerId)) {
                return true;
            }
        }
        return false;
    }

    private void send(Player player, String message) {
        try {
            if (player == null || player.getUser() == null) {
                return;
            }
            String phone = player.getUser().getPhoneNumber();
            if (phone != null && !phone.isBlank()) {
                whatsAppService.sendMessage(phone, message);
            }
        } catch (Exception e) {
            // best-effort
        }
    }

    private String safe(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
}
