package org.mifos.connector.phee.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Access tokens for the operations app (the BFF), from its {@code /oauth/token} endpoint.
 *
 * <p>
 * With {@code rest.authorization.enabled=true} the BFF answers {@code /api/v1/**} with 401 to a request that has no
 * token. A token is issued for one tenant (its audience), so one is taken per call, for the tenant of that call, with
 * the {@code operations-app.username} / {@code password} this connector already has. bulk-processor does the same for
 * its batch aggregate call. A BFF with authorization off ignores the token.
 */
@Component
public class OperationsAppTokens {

    /** The BFF's console client, which has an empty secret. */
    static final String CLIENT_ID = "client";

    private final OperationsAppProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OperationsAppTokens(OperationsAppProperties properties, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /** A bearer token for {@code tenant}. Throws IllegalStateException, naming the URL, if none is issued. */
    public String accessToken(String tenant) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Platform-TenantId", tenant);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", CLIENT_ID);
        form.add("client_secret", "");
        form.add("username", properties.username());
        form.add("password", properties.password());

        String url = properties.authUrl();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(form, headers),
                    String.class);
            JsonNode token = objectMapper.readTree(response.getBody()).get("access_token");
            if (token == null || token.asText().isBlank()) {
                throw new IllegalStateException("no access_token in the answer from " + url);
            }
            return token.asText();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not get an operations app token for tenant " + tenant + " from " + url
                    + ": " + e.getMessage(), e);
        }
    }
}
