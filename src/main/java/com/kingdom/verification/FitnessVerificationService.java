package com.kingdom.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * FITNESS verification: confirms a player did the activity by reading their Strava activities
 * ({@link StravaClient}).
 *
 * Pass rule: over the activities in [from, to] (optionally filtered to a sport type like "Run"), the chosen
 * metric must reach the target:
 *   - "distance_meters"     -> sum of distance      >= target
 *   - "moving_time_seconds" -> sum of moving_time   >= target
 *   - "activity_count"      -> number of activities >= target
 */
@Service
@RequiredArgsConstructor
public class FitnessVerificationService {

    private final StravaClient stravaClient;

    /**
     * Did the athlete reach {@code targetValue} for {@code metricKey} within [from, to]?
     * @param sportType optional Strava activity type to count only (e.g. "Run"); null/blank = count all.
     * @return true only if the summed metric meets the target. If Strava is off/unreachable, returns false.
     */
    public boolean hasReached(String metricKey, int targetValue, String sportType,
                              LocalDateTime from, LocalDateTime to) {
        return hasReached(metricKey, targetValue, sportType, from, to, null);
    }

    /**
     * Per-player overload: verify against the activities of the player whose Strava {@code refreshToken} is
     * given (null/blank -> the configured demo athlete). Used by finishChallenge so each player is checked
     * against THEIR OWN Strava (see StravaConnectService / ConnectedAccount provider=STRAVA).
     */
    public boolean hasReached(String metricKey, int targetValue, String sportType,
                              LocalDateTime from, LocalDateTime to, String refreshToken) {
        // Null-safe window (a finishChallenge wiring with null-date challenges must not NPE), and convert in
        // the SYSTEM zone so the after/before timestamps line up with the player's intended day boundaries.
        LocalDateTime fromTime = (from != null) ? from : LocalDateTime.now().minusYears(1);
        LocalDateTime toTime = (to != null) ? to : LocalDateTime.now();
        long after = fromTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        long before = toTime.atZone(ZoneId.systemDefault()).toEpochSecond();

        JsonNode activities = stravaClient.listActivities(after, before, refreshToken);
        if (activities == null || !activities.isArray()) {
            return false;
        }

        double total = 0;
        for (JsonNode activity : activities) {
            // Optional filter by sport type (e.g. only "Run" activities). Skipped when no type is given.
            if (sportType != null && !sportType.isBlank()
                    && !sportType.equalsIgnoreCase(activity.path("type").asText(""))) {
                continue;
            }
            switch (metricKey == null ? "" : metricKey) {
                case "distance_meters":     total += activity.path("distance").asDouble(0);    break;
                case "moving_time_seconds": total += activity.path("moving_time").asDouble(0); break;
                case "activity_count":      total += 1;                                         break;
                default:                    break; // unknown metric -> contributes nothing
            }
        }
        return total >= targetValue;
    }
}
