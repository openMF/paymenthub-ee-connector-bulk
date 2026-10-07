package org.mifos.connector.phee.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Everything under {@code mock-payment-schema}. Same property names as before. */
@Validated
@ConfigurationProperties(prefix = "mock-payment-schema")
public record MockPaymentSchemaProperties(
        @NotNull String contactpoint,
        @NotNull @Valid Endpoints endpoints) {

    public record Endpoints(@NotNull String batchSummary,
            @NotNull String batchDetail) {}

    public String batchSummaryUrl() {
        return contactpoint + endpoints.batchSummary();
    }

    public String batchDetailUrl() {
        return contactpoint + endpoints.batchDetail();
    }
}
