package com.kingdom.verification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;

/**
 * Strava client — verifies a FITNESS challenge by reading a player's recorded activities (runs, rides,
 * walks, workouts...). Feature-guarded: returns null when Strava is off / unconfigured / a call fails, so
 * callers fall back (never crash).
 *
 * PER-PLAYER: each player authorizes their OWN Strava (see {@link StravaConnectService}); their refresh
 * token is stored on a ConnectedAccount(provider=STRAVA) and passed to the read methods below. When no
 * per-player token is given, the methods fall back to the single configured demo athlete (strava.refresh-token).
 *
 * Strava access tokens expire every 6 hours, so we always REFRESH (using a long-lived refresh token) to get
 * a fresh access token, then call the activities endpoint.
 *
 * Config: strava.* in application.properties; real client-id/secret (+ optional demo refresh-token) in
 * application-local.properties.
 */
@Component
public class StravaClient {

    @Value("${strava.enabled:false}")
    private boolean enabled;

    @Value("${strava.client-id:}")
    private String clientId;

    @Value("${strava.client-secret:}")
    private String clientSecret;

    /** Optional single demo athlete used only when a player hasn't connected their own Strava. */
    @Value("${strava.refresh-token:}")
    private String demoRefreshToken;

    @Value("${strava.token-url:https://www.strava.com/api/v3/oauth/token}")
    private String tokenUrl;

    @Value("${strava.activities-url:https://www.strava.com/api/v3/athlete/activities}")
    private String activitiesUrl;

    @Value("${strava.authorize-url:https://www.strava.com/oauth/authorize}")
    private String authorizeBaseUrl;

    @Value("${strava.redirect-uri:http://localhost:8080/api/v1/verify/fitness/callback}")
    private String redirectUri;

    @Value("${strava.scope:activity:read_all}")
    private String scope;

    private final RestTemplate restTemplate = new RestTemplate();

    /** App-level config present (Strava on + client id/secret). The refresh token is per-player or the demo one. */
    public boolean isConfigured() {
        return enabled
                && clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    /** Back-compat alias. */
    public boolean isEnabled() {
        return isConfigured();
    }

    /**
     * Build the Strava OAuth authorize URL a player opens in a browser to grant access.
     * {@code state} is echoed back to our callback unchanged — we use it to carry the playerId.
     */
    public String authorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(authorizeBaseUrl.trim())
                .queryParam("client_id", clientId.trim())
                .queryParam("redirect_uri", redirectUri.trim())
                .queryParam("response_type", "code")
                .queryParam("approval_prompt", "auto")
                .queryParam("scope", scope.trim())
                .queryParam("state", state)
                .build().encode().toUriString();
    }

    /**
     * Exchange a one-time authorization code (from the callback) for tokens.
     * @return the JSON token response (refresh_token, access_token, expires_at, athlete{id}), or null on failure.
     */
    public JsonNode exchangeAuthorizationCode(String code) {
        if (!isConfigured() || code == null || code.isBlank()) {
            return null;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", clientId.trim());
            form.add("client_secret", clientSecret.trim());
            form.add("code", code.trim());
            form.add("grant_type", "authorization_code");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            return restTemplate.postForObject(tokenUrl.trim(), new HttpEntity<>(form, headers), JsonNode.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Refresh a given refresh token (a player's own, or the configured demo one when blank) into a fresh
     * access token (Strava tokens last only 6 hours).
     * @return the access_token, or null if Strava is off / unconfigured / no token / errored.
     */
    public String accessToken(String refreshToken) {
        String token = (refreshToken != null && !refreshToken.isBlank()) ? refreshToken : demoRefreshToken;
        if (!isConfigured() || token == null || token.isBlank()) {
            return null;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", clientId.trim());
            form.add("client_secret", clientSecret.trim());
            form.add("grant_type", "refresh_token");
            form.add("refresh_token", token.trim());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            JsonNode response = restTemplate.postForObject(
                    tokenUrl.trim(), new HttpEntity<>(form, headers), JsonNode.class);
            if (response == null) {
                return null;
            }
            JsonNode accessToken = response.path("access_token");
            return accessToken.isTextual() ? accessToken.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Back-compat: refresh the configured demo athlete's token. */
    public String accessToken() {
        return accessToken(null);
    }

    /**
     * List the activities (between two epoch-second timestamps) for the athlete identified by {@code refreshToken}
     * (a player's own token, or the configured demo athlete when null/blank).
     * @return the raw JSON array of activities, or null on failure. Each item has fields like:
     *         type ("Run"/"Ride"/"Walk"...), distance (metres), moving_time (seconds),
     *         elapsed_time (seconds), total_elevation_gain (metres), start_date (ISO-8601).
     */
    public JsonNode listActivities(long afterEpochSeconds, long beforeEpochSeconds, String refreshToken) {
        String token = accessToken(refreshToken);
        if (token == null) {
            return null;
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString(activitiesUrl.trim())
                    .queryParam("after", afterEpochSeconds)
                    .queryParam("before", beforeEpochSeconds)
                    .queryParam("per_page", 100)
                    .build().encode().toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            return response.getBody();
        } catch (Exception e) {
            return null;
        }
    }

    /** Back-compat: list the configured demo athlete's activities. */
    public JsonNode listActivities(long afterEpochSeconds, long beforeEpochSeconds) {
        return listActivities(afterEpochSeconds, beforeEpochSeconds, null);
    }
}
