package org.mifos.connector.phee.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class OperationsAppTokensTest {

    private static final String AUTH_URL = "http://paymenthub-ee-bff:80/oauth/token";

    private MockRestServiceServer server;
    private OperationsAppTokens tokens;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        OperationsAppProperties properties = new OperationsAppProperties("http://paymenthub-ee-bff:80", "mifos", "password",
                new OperationsAppProperties.Endpoints("/oauth/token", "/api/v1/batch", "/api/v1/batch/detail"));
        tokens = new OperationsAppTokens(properties, restTemplate, new ObjectMapper());
    }

    @Test
    void aPasswordGrantForTheRequestsTenantReturnsTheAccessToken() {
        server.expect(requestTo(AUTH_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Platform-TenantId", "redbank"))
                .andExpect(content().formDataContains(java.util.Map.of("grant_type", "password", "client_id", "client",
                        "username", "mifos", "password", "password")))
                .andRespond(withSuccess("{\"access_token\":\"abc\",\"token_type\":\"bearer\"}", MediaType.APPLICATION_JSON));

        assertThat(tokens.accessToken("redbank")).isEqualTo("abc");
        server.verify();
    }

    @Test
    void aRefusedLoginNamesTheTenantAndTheUrl() {
        server.expect(requestTo(AUTH_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> tokens.accessToken("redbank")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant redbank").hasMessageContaining(AUTH_URL);
    }

    @Test
    void anAnswerWithoutATokenIsAnError() {
        server.expect(requestTo(AUTH_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> tokens.accessToken("redbank")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no access_token");
    }
}
