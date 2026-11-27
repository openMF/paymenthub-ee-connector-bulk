package org.mifos.connector.phee.zeebe.workers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaseWorkerTest {

    @Test
    void aMissingVariableIsNamedInTheError() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("tenantId", null);

        assertThatThrownBy(() -> BaseWorker.requiredVariable(variables, "batchId"))
                .isInstanceOf(IllegalStateException.class).hasMessage("Zeebe variable 'batchId' is missing");
        assertThatThrownBy(() -> BaseWorker.requiredVariable(variables, "tenantId"))
                .hasMessage("Zeebe variable 'tenantId' is missing");
    }

    @Test
    void aPresentVariableIsReturnedAsText() {
        assertThat(BaseWorker.requiredVariable(Map.of("batchId", "B1", "count", 3), "batchId")).isEqualTo("B1");
        assertThat(BaseWorker.requiredVariable(Map.of("count", 3), "count")).isEqualTo("3");
    }
}
