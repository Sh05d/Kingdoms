package com.kingdom.Service.APIService;

import com.kingdom.API.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SteamService {
    @Value("${steam.api.key}")
    private String steamApiKey;

    private final RestTemplate restTemplate;

    public boolean verifyPlaytime(String steamId, String gameName, Integer targetMinutes) {
        JsonNode game = findOwnedGameByName(steamId, gameName);
        if (game == null) {
            return false;
        }
        int playtimeMinutes = game.path("playtime_forever").asInt(0);
        return playtimeMinutes >= targetMinutes;
    }

    public boolean verifyAchievement(String steamId, String gameName, String achievementName) {
        Integer appId = findAppIdByGameName(steamId, gameName);
        if (appId == null) {
            return false;
        }
        JsonNode achievements = getPlayerAchievements(steamId, appId);
        for (JsonNode achievement : achievements) {
            String displayName = achievement.path("displayName").asText("");
            String apiName    = achievement.path("apiname").asText("");
            String name       = achievement.path("name").asText("");
            int achieved      = achievement.path("achieved").asInt(0);
            if (achieved == 1 && (
                    displayName.equalsIgnoreCase(achievementName)
                            || apiName.equalsIgnoreCase(achievementName)
                            || name.equalsIgnoreCase(achievementName)
            )) {
                return true;
            }
        }
        return false;
    }

    public boolean verifyAchievementCount(String steamId, String gameName, Integer targetCount) {
        Integer appId = findAppIdByGameName(steamId, gameName);
        if (appId == null) {
            return false;
        }
        JsonNode achievements = getPlayerAchievements(steamId, appId);
        int achievedCount = 0;
        for (JsonNode achievement : achievements) {
            if (achievement.path("achieved").asInt(0) == 1) {
                achievedCount++;
            }
        }
        return achievedCount >= targetCount;
    }

    private Integer findAppIdByGameName(String steamId, String gameName) {
        JsonNode game = findOwnedGameByName(steamId, gameName);
        if (game == null) {
            return null;
        }
        return game.path("appid").asInt();
    }

    private JsonNode findOwnedGameByName(String steamId, String gameName) {
        String url = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/"
                + "?key=" + steamApiKey
                + "&steamid=" + steamId
                + "&include_appinfo=true"
                + "&include_played_free_games=true"
                + "&format=json";

        String raw = restTemplate.getForObject(url, String.class);
        if (raw == null) {
            throw new ApiException("Steam returned empty response");
        }

        try {
            JsonNode response = new ObjectMapper().readTree(raw);
            JsonNode games = response.path("response").path("games");
            if (!games.isArray()) {
                throw new ApiException("Steam games are private or unavailable");
            }
            for (JsonNode game : games) {
                String steamGameName = game.path("name").asText("");
                if (steamGameName.equalsIgnoreCase(gameName)) {
                    return game;
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Failed to parse Steam response: " + e.getMessage());
        }

        return null;
    }

    private JsonNode getPlayerAchievements(String steamId, Integer appId) {
        String url = "https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v0001/"
                + "?key=" + steamApiKey
                + "&steamid=" + steamId
                + "&appid=" + appId
                + "&l=english"
                + "&format=json";

        String raw = restTemplate.getForObject(url, String.class);
        if (raw == null) {
            throw new ApiException("Steam returned empty response");
        }

        try {
            JsonNode response = new ObjectMapper().readTree(raw);
            JsonNode achievements = response.path("playerstats").path("achievements");
            if (!achievements.isArray()) {
                throw new ApiException("Steam achievements are private or unavailable");
            }
            return achievements;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Failed to parse Steam response: " + e.getMessage());
        }
    }
}
