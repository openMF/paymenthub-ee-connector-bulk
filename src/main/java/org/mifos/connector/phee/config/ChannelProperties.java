package org.mifos.connector.phee.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Everything under {@code channel}. Same property names as before. */
@Validated
@ConfigurationProperties(prefix = "channel")
public record ChannelProperties(@NotNull String contactpoint,
        @NotNull @Valid Endpoints endpoints) {

    public record Endpoints(@NotNull String transfer) {}

    public String transferUrl() {
        return contactpoint + endpoints.transfer();
    }
}
