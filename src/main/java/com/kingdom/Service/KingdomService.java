package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.DTO.IN.KingdomIN;
import com.kingdom.DTO.IN.KingdomRecommendationIn;
import com.kingdom.DTO.OUT.KingdomOut;
import com.kingdom.DTO.OUT.KingdomRecommendationOut;
import com.kingdom.DTO.OUT.LandControlSummaryOut;
import com.kingdom.DTO.OUT.LeaderboardOut;
import com.kingdom.Enums.KingdomType;
import com.kingdom.Enums.Period;
import com.kingdom.Model.Kingdom;
import com.kingdom.Model.KingdomMembership;
import com.kingdom.Model.PeriodScore;
import com.kingdom.Model.Player;
import com.kingdom.Repository.KingdomMembershipRepository;
import com.kingdom.Repository.KingdomRepository;
import com.kingdom.Repository.PeriodScoreRepository;
import com.kingdom.Repository.PlayerRepository;
import com.kingdom.Service.AiService.RecommendationAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KingdomService {

    private final KingdomRepository kingdomRepository;
    private final KingdomMembershipRepository kingdomMembershipRepository;
    private final PeriodScoreRepository periodScoreRepository;
    private final PlayerRepository playerRepository;
    private final RecommendationAiService recommendationAiService;


    public List<KingdomOut> getAllKingdoms() {

        List<KingdomOut> kingdomDTOs = new ArrayList<>();

        for (Kingdom kingdom : kingdomRepository.findAll()) {
            kingdomDTOs.add(convertToOut(kingdom));
        }

        return kingdomDTOs;
    }

    public void addKingdom(KingdomIN kingdomIN) {
        Kingdom kingdom = new Kingdom();
        kingdom.setName(kingdomIN.getName());
        kingdom.setDescription(kingdomIN.getDescription());
        kingdom.setType(kingdomIN.getType());

        kingdomRepository.save(kingdom);
    }

    public KingdomOut getKingdomById(Integer id) {
        Kingdom kingdom = kingdomRepository.findKingdomById(id);
        if (kingdom == null) {
            throw new ApiException("Kingdom not found");
        }
        return convertToOut(kingdom);
    }

    public void updateKingdom(Integer id, KingdomIN kingdomIN) {
        Kingdom oldKingdom = findKingdomById(id);

        oldKingdom.setName(kingdomIN.getName());
        oldKingdom.setDescription(kingdomIN.getDescription());
        oldKingdom.setType(kingdomIN.getType());

        kingdomRepository.save(oldKingdom);
    }

    public void deleteKingdom(Integer id) {
        Kingdom kingdom = findKingdomById(id);
        kingdomRepository.delete(kingdom);
    }

    public List<LandControlSummaryOut> landControlSummary(Integer kingdomId, Integer division) {

        Kingdom kingdom = findKingdomById(kingdomId);

        List<KingdomMembership> memberships = kingdomMembershipRepository.membersInDivision(kingdom.getId(),division);

        int totalXP = 0;
        for (KingdomMembership membership : memberships) {
            totalXP += membership.getTotalXP();
        }

        List<LandControlSummaryOut> result = new ArrayList<>();

        for (KingdomMembership membership : memberships) {

            int playerXP = (membership.getTotalXP());

            double percentage = 0.0;

            if (totalXP != 0) {
                percentage = (playerXP * 100.0) / totalXP;
            }

            result.add(new LandControlSummaryOut(membership.getPlayer().getDisplayName(), Math.round(percentage * 100.0) / 100.0));
        }

        return result;
    }

    public List<LeaderboardOut> leaderboardByPeriod(Integer kingdomId, Period period) {
        List<PeriodScore> scores = periodScoreRepository.leaderboardByPeriod(kingdomId, period, LocalDateTime.now());

        return buildLeaderboard(scores);
    }

    public List<LeaderboardOut> leaderboardByPeriodAndDivision(Integer kingdomId, Integer division, Period period) {
        List<PeriodScore> scores = periodScoreRepository.leaderboardByPeriodAndDivision(kingdomId, division, period, LocalDateTime.now());

        return buildLeaderboard(scores);
    }

    private List<LeaderboardOut> buildLeaderboard(List<PeriodScore> scores) {

        List<LeaderboardOut> result = new ArrayList<>();

        int rank = 1;

        for (PeriodScore score : scores) {

            result.add(new LeaderboardOut(rank, score.getKingdomMembership().getPlayer().getDisplayName(), score.getSeasonalXp()));

            rank++;
        }

        return result;
    }

    public List<LeaderboardOut> leaderboardByDivision(Integer kingdomId, Integer division) {

        List<KingdomMembership> memberships = kingdomMembershipRepository.leaderboardByDivision(kingdomId, division);

        List<LeaderboardOut> result = new ArrayList<>();

        int rank = 1;

        for (KingdomMembership membership : memberships) {

            result.add(new LeaderboardOut(rank, membership.getPlayer().getDisplayName(), membership.getTotalXP()));

            rank++;
        }

        return result;
    }

    public KingdomOut convertToOut(Kingdom kingdom) {

        return new KingdomOut(
                kingdom.getName(),
                kingdom.getDescription(),
                kingdom.getType()
        );
    }

    public Kingdom findKingdomById(Integer id) {
        Kingdom kingdom = kingdomRepository.findKingdomById(id);
        if (kingdom == null) {
            throw new ApiException("Kingdom not found");
        }
        return kingdom;
    }

    public KingdomRecommendationOut recommendKingdom(Integer playerId, KingdomRecommendationIn recommendationIN) {

        Player player = playerRepository.findPlayerById(playerId);

        if (player == null) {
            throw new ApiException("Player not found");
        }

        if (recommendationIN.getInterests() == null || recommendationIN.getInterests().isBlank()) {
            throw new ApiException("Interest is required");
        }

        player.setInterests(recommendationIN.getInterests());
        playerRepository.save(player);

        List<KingdomMembership> memberships = kingdomMembershipRepository.findByPlayer(player);

        List<KingdomType> joinedTypes = memberships.stream().map(membership -> membership.getKingdom().getType()).toList();

        List<Kingdom> availableKingdoms = kingdomRepository.findAll().stream().filter(kingdom -> !joinedTypes.contains(kingdom.getType())).toList();

        if (availableKingdoms.isEmpty()) {
            return new KingdomRecommendationOut(
                    0,
                    "جميع الممالك",
                    "أنت مشترك في جميع الممالك المتوفرة حاليًا 👑"
            );
        }

        List<String> joinedTypesText = joinedTypes.stream().map(Enum::name).toList();

        List<String> availableTypesText = availableKingdoms.stream().map(kingdom -> kingdom.getType().name()).toList();

        String[] aiResult = recommendationAiService.recommendKingdom(player, recommendationIN.getInterests(), joinedTypesText, availableTypesText);

        KingdomType recommendedType;

        try {
            recommendedType = KingdomType.valueOf(aiResult[0]);
        } catch (Exception e) {
            throw new ApiException("Invalid kingdom type from AI");
        }

        String reason = aiResult[1];

        Kingdom recommendedKingdom = availableKingdoms.stream().filter(kingdom -> kingdom.getType() == recommendedType).findFirst().orElseThrow(() -> new ApiException("AI recommended invalid kingdom"));

        return new KingdomRecommendationOut(recommendedKingdom.getId(), recommendedKingdom.getName(), reason);
    }

}
