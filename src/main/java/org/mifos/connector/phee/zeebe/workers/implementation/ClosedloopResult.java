package org.mifos.connector.phee.zeebe.workers.implementation;

import java.math.BigDecimal;
import java.util.List;
import org.mifos.connector.phee.schema.Transaction;

/**
 * The outcome of a closedloop batch, transaction by transaction.
 *
 * <p>
 * The figures are summed from the transactions that actually succeeded or failed, wherever they sit in the file. The
 * code this replaces returned only two counters and took the failed amount as the last {@code failureCount} rows, so a
 * failure anywhere but at the end of the file was reported against the wrong amount.
 */
public record ClosedloopResult(List<Transaction> succeeded, List<Transaction> failed) {

    public ClosedloopResult {
        succeeded = List.copyOf(succeeded);
        failed = List.copyOf(failed);
    }

    public int successCount() {
        return succeeded.size();
    }

    public int failureCount() {
        return failed.size();
    }

    public long total() {
        return (long) succeeded.size() + failed.size();
    }

    public double completedAmount() {
        return sum(succeeded);
    }

    public double failedAmount() {
        return sum(failed);
    }

    public double totalAmount() {
        return BigDecimal.valueOf(completedAmount()).add(BigDecimal.valueOf(failedAmount())).doubleValue();
    }

    private static double sum(List<Transaction> transactions) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Transaction transaction : transactions) {
            if (transaction.getAmount() != null && !transaction.getAmount().isBlank()) {
                sum = sum.add(new BigDecimal(transaction.getAmount().trim()));
            }
        }
        return sum.doubleValue();
    }
}
