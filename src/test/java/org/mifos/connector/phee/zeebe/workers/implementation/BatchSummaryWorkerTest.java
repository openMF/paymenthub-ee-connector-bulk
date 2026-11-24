package org.mifos.connector.phee.zeebe.workers.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mifos.connector.phee.config.MockPaymentSchemaProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * The failure branch used to read ERROR_CODE and ERROR_DESCRIPTION from an exchange no route ever saw, so it always
 * reported {@code null, null}. The reason now comes from the summary call itself.
 */
class BatchSummaryWorkerTest {

    private static final String URL = "http://mock-payment-schema:8080/batches/B1/summary";

    private BatchSummaryWorker worker;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        worker = new BatchSummaryWorker();
        worker.mockPaymentSchemaProperties = new MockPaymentSchemaProperties("http://mock-payment-schema:8080",
                new MockPaymentSchemaProperties.Endpoints("/summary", "/detail"));
        ReflectionTestUtils.setField(worker, "restTemplate", restTemplate);
    }

    @Test
    void aSummaryIsReturnedWithNoError() {
        server.expect(requestTo(URL)).andExpect(header("Platform-TenantId", "greenbank"))
                .andRespond(withSuccess("{\"batchId\":\"B1\",\"total\":2,\"successful\":2,\"failed\":0,\"ongoing\":0}",
                        MediaType.APPLICATION_JSON));

        BatchSummaryWorker.SummaryResponse response = worker.callApi("B1", "greenbank");

        assertThat(response.batch().getTotal()).isEqualTo(2L);
        assertThat(response.errorCode()).isNull();
        assertThat(response.errorDescription()).isNull();
        server.verify();
    }

    @Test
    void anHttpErrorIsReportedWithItsStatusAndBody() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND).body("no such batch"));

        BatchSummaryWorker.SummaryResponse response = worker.callApi("B1", "greenbank");

        assertThat(response.batch()).isNull();
        assertThat(response.errorCode()).isEqualTo("404");
        assertThat(response.errorDescription()).contains("404").contains("no such batch");
    }

    @Test
    void aServerErrorIsReportedToo() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        BatchSummaryWorker.SummaryResponse response = worker.callApi("B1", "greenbank");

        assertThat(response.errorCode()).isEqualTo("500");
    }

    @Test
    void aCallThatNeverGetsAnAnswerIsReportedAsFailed() {
        worker.mockPaymentSchemaProperties = new MockPaymentSchemaProperties("http://127.0.0.1:1",
                new MockPaymentSchemaProperties.Endpoints("/summary", "/detail"));
        ReflectionTestUtils.setField(worker, "restTemplate", new RestTemplate());

        BatchSummaryWorker.SummaryResponse response = worker.callApi("B1", "greenbank");

        assertThat(response.batch()).isNull();
        assertThat(response.errorCode()).isEqualTo(BatchSummaryWorker.SUMMARY_CALL_FAILED);
        assertThat(response.errorDescription()).startsWith("Batch summary call failed:");
    }
}
