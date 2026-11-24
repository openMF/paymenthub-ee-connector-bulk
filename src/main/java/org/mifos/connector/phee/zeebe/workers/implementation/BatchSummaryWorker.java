package org.mifos.connector.phee.zeebe.workers.implementation;

import org.mifos.connector.phee.config.MockPaymentSchemaProperties;
import org.mifos.connector.phee.schema.BatchDTO;
import org.mifos.connector.phee.zeebe.workers.BaseWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.mifos.connector.phee.zeebe.ZeebeVariables.BATCH_ID;
import static org.mifos.connector.phee.zeebe.ZeebeVariables.BATCH_SUMMARY_SUCCESS;
import static org.mifos.connector.phee.zeebe.ZeebeVariables.COMPLETED_AMOUNT;
import static org.mifos.connector.phee.zeebe.ZeebeVariables.COMPLETED_TRANSACTION;
import static org.mifos.connector.phee.zeebe.ZeebeVariables.COMPLETION_RATE;
import static org.mifos.connector.phee.zeebe.ZeebeVariables.CURRENT_RETRY_COUNT;
import static org.mifos.connector.phee.zeebe.ZeebeVariables.ERROR_CODE;
import static org.mifos.connector.phee.zeebe.ZeebeVariables.ERROR_DESCRIPTION;
import static org.mifos.connector.phee.zeebe.ZeebeVariables.FAILED_AMOUNT;
import static org.mifos.connector.phee.zeebe.ZeebeVariables.FAILED_TRANSACTION;
import static org.mifos.connector.phee.zeebe.ZeebeVariables.MAX_RETRY_COUNT;
import static org.mifos.connector.phee.zeebe.ZeebeVariables.ONGOING_AMOUNT;
import static org.mifos.connector.phee.zeebe.ZeebeVariables.ONGOING_TRANSACTION;
import static org.mifos.connector.phee.zeebe.ZeebeVariables.TENANT_ID;
import static org.mifos.connector.phee.zeebe.ZeebeVariables.TOTAL_AMOUNT;
import static org.mifos.connector.phee.zeebe.ZeebeVariables.TOTAL_TRANSACTION;
import static org.mifos.connector.phee.zeebe.workers.Worker.BATCH_SUMMARY;


@Component
public class BatchSummaryWorker extends BaseWorker {

    @Value("${config.completion-threshold-check.max-retry-count}")
    public int maxRetryCount;
    @Autowired
    public MockPaymentSchemaProperties mockPaymentSchemaProperties;

    @Autowired
    @Qualifier("trustAllRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public void setup() {
        logger.info("## generating " + BATCH_SUMMARY + "zeebe worker");
        newWorker(BATCH_SUMMARY, (client, job)->{
            Map<String, Object> variables = job.getVariablesAsMap();
            int currentRetryCount = (int) variables.getOrDefault(CURRENT_RETRY_COUNT, 1);

            String batchId = requiredVariable(variables, BATCH_ID);
            SummaryResponse summary = callApi(batchId, requiredVariable(variables, TENANT_ID));
            BatchDTO batchDTO = summary.batch();
            logger.info("Batch Summary: {}", objectMapper.writeValueAsString(batchDTO));

            variables.put(MAX_RETRY_COUNT, maxRetryCount);
            variables.put(CURRENT_RETRY_COUNT, ++currentRetryCount);

            // Check if Zeebe already has counts pre-populated (closedloop path sets these in BatchTransferWorker)
            long preSetTotal = variables.containsKey(TOTAL_TRANSACTION) ?
                    ((Number) variables.get(TOTAL_TRANSACTION)).longValue() : 0L;
            boolean hasMockPaymentSchemaData = batchDTO != null && batchDTO.getTotal() != null && batchDTO.getTotal() > 0;

            if (hasMockPaymentSchemaData) {
                // Normal path (mojaloop): use mock-payment-schema values
                variables.put(ONGOING_TRANSACTION, batchDTO.getOngoing());
                variables.put(FAILED_TRANSACTION, batchDTO.getFailed());
                variables.put(TOTAL_TRANSACTION, batchDTO.getTotal());
                variables.put(COMPLETED_TRANSACTION, batchDTO.getSuccessful());
                variables.put(ONGOING_AMOUNT, batchDTO.getPendingAmount());
                variables.put(FAILED_AMOUNT, batchDTO.getFailedAmount());
                variables.put(COMPLETED_AMOUNT, batchDTO.getSuccessfulAmount());
                variables.put(TOTAL_AMOUNT, batchDTO.getTotalAmount());
                long percentage = (long)(((double)
                        (batchDTO.getSuccessful() + batchDTO.getFailed()) / batchDTO.getTotal()) * 100);
                variables.put(COMPLETION_RATE, percentage);
                variables.put(BATCH_SUMMARY_SUCCESS, true);
            } else if (preSetTotal > 0) {
                // Closedloop path: counts already set by BatchTransferWorker — keep them, just signal success
                logger.info("mock-payment-schema returned 0/null total but Zeebe has pre-set counts (total={}). Using pre-set values (closedloop path).", preSetTotal);
                variables.put(BATCH_SUMMARY_SUCCESS, true);
            } else {
                // The BPMN's retry gateway tests batchSummarySuccess = false; leaving it unset matches no flow.
                String errorCode = summary.errorCode() != null ? summary.errorCode() : NO_SUMMARY;
                String errorDescription = summary.errorDescription() != null ? summary.errorDescription()
                        : "mock-payment-schema returned no transactions for batch " + batchId;
                variables.put(BATCH_SUMMARY_SUCCESS, false);
                variables.put(ERROR_CODE, errorCode);
                variables.put(ERROR_DESCRIPTION, errorDescription);
                logger.warn("Batch summary unavailable for batch {} (attempt {} of {}): {}, {}", batchId,
                        currentRetryCount - 1, maxRetryCount, errorCode, errorDescription);
            }

            client.newCompleteCommand(job.getKey()).variables(variables).send();
        });
    }

    /** The summary, or why there is none. */
    public record SummaryResponse(BatchDTO batch, String errorCode, String errorDescription) {
    }

    static final String NO_SUMMARY = "BATCH_SUMMARY_EMPTY";
    static final String SUMMARY_CALL_FAILED = "BATCH_SUMMARY_CALL_FAILED";

    public SummaryResponse callApi(String batchId, String tenant) {
        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Platform-TenantId", tenant);

        // Construct URL
        String apiUrl = mockPaymentSchemaProperties.contactpoint() + "/batches/" + batchId + "/summary";

        // Construct request entity with headers
        HttpEntity<String> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<BatchDTO> response = restTemplate.exchange(apiUrl, HttpMethod.GET, requestEntity, BatchDTO.class);
            logger.info("Batch summary API response: \n\n" + response.getBody());
            return new SummaryResponse(response.getBody(), null, null);
        } catch (HttpStatusCodeException e) {
            logger.warn("Batch summary call to {} answered {}: {}", apiUrl, e.getStatusCode().value(), e.getResponseBodyAsString());
            return new SummaryResponse(null, String.valueOf(e.getStatusCode().value()),
                    "Batch summary call answered " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            logger.warn("Batch summary call to {} failed: {}", apiUrl, e.getMessage());
            return new SummaryResponse(null, SUMMARY_CALL_FAILED, "Batch summary call failed: " + e.getMessage());
        }
    }
}
