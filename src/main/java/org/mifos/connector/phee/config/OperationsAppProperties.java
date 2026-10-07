package org.mifos.connector.phee.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Everything under {@code operations-app}.
 *
 * <p>
 * The property names are exactly the ones that were on the {@code @Value} annotations before, because the operator sets
 * them as environment variables ({@code OPERATIONS_APP_CONTACTPOINT},
 * {@code OPERATIONS_APP_ENDPOINTS_BATCH_SUMMARY} and so on). Renaming one would silently break a deployment.
 */
@Validated
@ConfigurationProperties(prefix = "operations-app")
public record OperationsAppProperties(@NotNull String contactpoint,
        @NotNull String username, @NotNull String password, @NotNull @Valid Endpoints endpoints) {

    public record Endpoints(@NotNull String auth,
            @NotNull String batchSummary,
            @NotNull String batchDetail) {}

    public String batchSummaryUrl() {
        return contactpoint + endpoints.batchSummary();
    }

    public String batchDetailUrl() {
        return contactpoint + endpoints.batchDetail();
    }

    public String authUrl() {
        return contactpoint + endpoints.auth();
    }
}
