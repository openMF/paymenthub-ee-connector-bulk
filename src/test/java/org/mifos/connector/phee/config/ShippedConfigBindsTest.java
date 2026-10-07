package org.mifos.connector.phee.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Every property these records ask for has to be there, or the connector must refuse to start and say which one is
 * missing. That is what the plain {@code @Value} declarations did before they were replaced, so these tests hold the
 * replacement to the same promise, and they read the real application.yaml rather than a copy of it.
 *
 * <p>
 * Every field of these records is a string, so there is no number or boolean that an empty value could break: an empty
 * value is accepted, as it was before.
 * </p>
 */
class ShippedConfigBindsTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ OperationsAppProperties.class, MockPaymentSchemaProperties.class,
            BulkProcessorProperties.class, ChannelProperties.class })
    static class AllRecords {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OperationsAppProperties.class)
    static class OnlyOperationsApp {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MockPaymentSchemaProperties.class)
    static class OnlyMockPaymentSchema {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BulkProcessorProperties.class)
    static class OnlyBulkProcessor {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ChannelProperties.class)
    static class OnlyChannel {}

    /** One configuration class per record, keyed by the prefix that record binds. */
    private static final Map<String, Class<?>> ONE_RECORD_EACH = Map.of("operations-app", OnlyOperationsApp.class,
            "mock-payment-schema", OnlyMockPaymentSchema.class, "bulk-processor", OnlyBulkProcessor.class, "channel",
            OnlyChannel.class);

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(
                AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class));
    }

    private ApplicationContextRunner withShippedYaml() {
        return runner().withInitializer(new ConfigDataApplicationContextInitializer());
    }

    @Test
    void theShippedApplicationYamlFillsEveryField() {
        withShippedYaml().withUserConfiguration(AllRecords.class).run(context -> {
            assertThat(context).hasNotFailed();
            OperationsAppProperties operations = context.getBean(OperationsAppProperties.class);
            assertThat(operations.username()).isEqualTo("mifos");
            assertThat(operations.password()).isNotEmpty();
            assertThat(operations.authUrl()).isEqualTo("https://ops-bk.mifos.gazelle.test/oauth/token");
            assertThat(operations.batchSummaryUrl()).isEqualTo("https://ops-bk.mifos.gazelle.test/api/v1/batch");
            assertThat(operations.batchDetailUrl()).isEqualTo("https://ops-bk.mifos.gazelle.test/api/v1/batch/detail");
            MockPaymentSchemaProperties mock = context.getBean(MockPaymentSchemaProperties.class);
            assertThat(mock.batchSummaryUrl()).isEqualTo("http://paymenthub-ee-connector-mock-payment-schema:8080/mockapi/v1/batch/summary");
            assertThat(mock.batchDetailUrl()).isEqualTo("http://paymenthub-ee-connector-mock-payment-schema:8080/mockapi/v1/batch/detail");
            BulkProcessorProperties bulkProcessor = context.getBean(BulkProcessorProperties.class);
            assertThat(bulkProcessor.batchTransactionUrl()).isEqualTo("https://paymenthub-ee-bulk-processor:8443/batchtransactions");
            assertThat(bulkProcessor.batchExecutionUrl())
                    .isEqualTo("https://paymenthub-ee-bulk-processor:8443/batchtransactions/execution");
            assertThat(context.getBean(ChannelProperties.class).transferUrl())
                    .isEqualTo("https://paymenthub-ee-connector-channel:8443/channel/transfer");
        });
    }

    @Test
    void everyRecordRefusesToStartWhenItsSectionIsMissing() {
        ONE_RECORD_EACH.forEach((prefix, configuration) -> runner().withUserConfiguration(configuration).run(context -> {
            assertThat(context).as("context with nothing configured under '%s'", prefix).hasFailed();
            assertThat(context.getStartupFailure()).as("failure for '%s'", prefix).hasStackTraceContaining("BindValidationException")
                    .hasStackTraceContaining("Binding validation errors on " + prefix);
        }));
    }

    @Test
    void aMissingKeyInsideAGroupStopsStartup() {
        // only endpoints.auth is missing, so this checks that @Valid reaches the nested record
        runner().withUserConfiguration(OnlyOperationsApp.class)
                .withPropertyValues("operations-app.contactpoint=http://ops", "operations-app.username=u", "operations-app.password=p",
                        "operations-app.endpoints.batch-summary=/s", "operations-app.endpoints.batch-detail=/d")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("Binding validation errors on operations-app")
                            .hasStackTraceContaining("endpoints.auth");
                });
    }

    @Test
    void aValueSetToNothingOnAStringFieldIsAcceptedJustAsItWasBefore() {
        withShippedYaml().withUserConfiguration(OnlyChannel.class).withPropertyValues("channel.endpoints.transfer=").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ChannelProperties.class).endpoints().transfer()).isEmpty();
        });
    }
}
