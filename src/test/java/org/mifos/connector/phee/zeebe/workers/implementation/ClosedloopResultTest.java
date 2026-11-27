package org.mifos.connector.phee.zeebe.workers.implementation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mifos.connector.phee.schema.Transaction;

class ClosedloopResultTest {

    private static Transaction transaction(int id, String amount) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setAmount(amount);
        return transaction;
    }

    @Test
    void aFailureInTheMiddleOfTheFileIsReportedAgainstItsOwnAmount() {
        Transaction first = transaction(0, "10");
        Transaction middle = transaction(1, "20");
        Transaction last = transaction(2, "30");

        ClosedloopResult result = new ClosedloopResult(List.of(first, last), List.of(middle));

        // the old .skip(successCount) arithmetic gave failed=30, completed=30
        assertThat(result.failedAmount()).isEqualTo(20.0);
        assertThat(result.completedAmount()).isEqualTo(40.0);
        assertThat(result.totalAmount()).isEqualTo(60.0);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(3);
    }

    @Test
    void decimalAmountsAddUpExactly() {
        ClosedloopResult result = new ClosedloopResult(List.of(transaction(0, "0.10"), transaction(1, "0.20")),
                List.of());

        assertThat(result.completedAmount()).isEqualTo(0.3);
        assertThat(result.failedAmount()).isZero();
    }

    @Test
    void aMissingAmountCountsAsZero() {
        ClosedloopResult result = new ClosedloopResult(List.of(transaction(0, null)), List.of(transaction(1, " ")));

        assertThat(result.totalAmount()).isZero();
        assertThat(result.total()).isEqualTo(2);
    }
}
