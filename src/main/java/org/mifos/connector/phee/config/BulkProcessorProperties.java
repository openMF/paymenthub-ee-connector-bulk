package org.mifos.connector.phee.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Everything under {@code bulk-processor}. Same property names as before. */
@Validated
@ConfigurationProperties(prefix = "bulk-processor")
public record BulkProcessorProperties(@NotNull String contactpoint,
        @NotNull @Valid Endpoints endpoints) {

    public record Endpoints(@NotNull String batchTransaction,
            @NotNull String batchExecution) {}

    public String batchTransactionUrl() {
        return contactpoint + endpoints.batchTransaction();
    }

    public String batchExecutionUrl() {
        return contactpoint + endpoints.batchExecution();
    }
}
