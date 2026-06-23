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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Neotek Open Banking (Saudi) client — used to verify a CHARITY donation by reading the player's bank
 * transactions. Feature-guarded like {@link com.kingdom.Service.AiService.OpenAiClient}: returns null when
 * Neotek is disabled / unconfigured / a call fails (callers fall back, never crash).
 *
 * Confirmed flow (sandbox): token("ob_connect") -> createAccountsLink(SAIBCSARI, psuId) -> player opens the
 * RedirectionURL + approves at the mock bank -> token("single_api") -> listAccounts/listTransactions(psuId).
 * There is NO separate "create profile" step — accounts + transactions are read directly by PSUId.
 *
 * Config: neotek.* in application.properties; real client-id/secret in application-local.properties.
 */
@Component
public class NeotekClient {

    @Value("${neotek.enabled:false}")
    private boolean enabled;

    @Value("${neotek.base-url:https://test.api.neotek.sa}")
    private String baseUrl;

    @Value("${neotek.token-url:https://test.api.neotek.sa/oauth2security/oauth2/token}")
    private String tokenUrl;

    @Value("${neotek.client-id:}")
    private String clientId;

    @Value("${neotek.client-secret:}")
    private String clientSecret;

    // Reading accounts/transactions uses scope "single_api"; creating the consent link uses "ob_connect".
    @Value("${neotek.scope-read:single_api}")
    private String scopeRead;

    @Value("${neotek.scope-connect:ob_connect}")
    private String scopeConnect;

    @Value("${neotek.accounts-link-path:/accounts-information/v1/accounts-links}")
    private String accountsLinkPath;

    @Value("${neotek.transactions-path:/single-api-aggregator/v1/profiles/transactions}")
    private String transactionsPath;

    @Value("${neotek.accounts-path:/single-api-aggregator/v1/profiles/accounts}")
    private String accountsPath;

    // Sandbox bank wired for consent (Saib / Saudi Investment Bank). BSFR/INMA/SAMAModelBank 500 here.
    @Value("${neotek.financial-institution-id:SAIBCSARI}")
    private String financialInstitutionId;

    private final RestTemplate restTemplate = new RestTemplate();

    /** True only when Neotek is switched on AND credentials are present. */
    public boolean isEnabled() {
        return enabled
                && clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    /** The default sandbox bank id used for the consent flow. */
    public String defaultFinancialInstitutionId() {
        return financialInstitutionId;
    }

    /**
     * Get an OAuth2 access token for the given scope ("single_api" to read, "ob_connect" to create a link).
     * @return the access_token, or null if Neotek is off / unconfigured / errored.
     */
    public String token(String scope) {
        if (!isEnabled()) {
            return null;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId.trim());
            form.add("client_secret", clientSecret.trim());
            form.add("scope", scope);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            JsonNode response = restTemplate.postForObject(
                    tokenUrl.trim(), new HttpEntity<>(form, headers), JsonNode.class);
            if (response == null) {
                return null;
            }
            JsonNode token = response.path("access_token");
            return token.isTextual() ? token.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Create an account-link (consent). For the "Redirection" profile the response carries a RedirectionURL
     * the player opens to log in + approve at the (sandbox mock) bank.
     *
     * @param financialInstitutionId the bank id (use {@link #defaultFinancialInstitutionId()} = SAIBCSARI in sandbox)
     * @param psuId                  your identifier for the player (stored as ConnectedAccount.externalUserId)
     * @return the raw response JSON — read {@code Data.AccountsLinkId} and {@code Data.RedirectionURL} — or null.
     */
    public JsonNode createAccountsLink(String financialInstitutionId, String psuId,
                                       LocalDateTime transactionsFrom, LocalDateTime transactionsTo) {
        String accessToken = token(scopeConnect);
        if (accessToken == null) {
            return null;
        }
        try {
            // Two data groups, exactly the shape the sandbox accepts (account details + transactions).
            Map<String, Object> accountDetails = new LinkedHashMap<>();
            accountDetails.put("DataGroupId", "AccountDetails");
            accountDetails.put("Permissions", List.of("ReadAccountsBasic", "ReadAccountsDetail"));

            Map<String, Object> transactions = new LinkedHashMap<>();
            transactions.put("DataGroupId", "AccountTransactions");
            transactions.put("Permissions", List.of("ReadTransactionsBasic", "ReadTransactionsDetail", "ReadTransactionsDebits"));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("FinancialInstitutionId", financialInstitutionId);
            data.put("SecurityProfile", "Redirection");
            data.put("DataGroups", List.of(accountDetails, transactions));
            data.put("PSUId", psuId);
            if (transactionsFrom != null) {
                data.put("TransactionFromDateTime", transactionsFrom.toString());
            }
            if (transactionsTo != null) {
                data.put("TransactionToDateTime", transactionsTo.toString());
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Data", data);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            return restTemplate.postForObject(
                    baseUrl.trim() + accountsLinkPath, new HttpEntity<>(body, headers), JsonNode.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** List the consented PSU's accounts. Read {@code Data.Accounts[]}. Null on failure. */
    public JsonNode listAccounts(String psuId) {
        return getByPsu(accountsPath, psuId);
    }

    /** List the consented PSU's transactions. Read {@code Data.Transactions[]}. Null on failure. */
    public JsonNode listTransactions(String psuId) {
        return getByPsu(transactionsPath, psuId);
    }

    // Shared GET .../{path}?PSUId=... with a fresh single_api token.
    private JsonNode getByPsu(String path, String psuId) {
        String accessToken = token(scopeRead);
        if (accessToken == null) {
            return null;
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString(baseUrl.trim() + path)
                    .queryParam("PSUId", psuId)
                    .build().encode().toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            return response.getBody();
        } catch (Exception e) {
            return null;
        }
    }
}
