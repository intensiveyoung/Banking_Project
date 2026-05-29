package domain;

import java.time.LocalDateTime;

public final class Transaction {
    private final TransactionType type;
    private final double amount;
    private final LocalDateTime timestamp;
    private final Double resultingBalance; // Nullable for failed transactions
    private final TransactionStatus status;

    public Transaction(TransactionType type, double amount, LocalDateTime timestamp,
                       Double resultingBalance, TransactionStatus status) {
        this.type = type;
        this.amount = amount;
        this.timestamp = timestamp;
        this.resultingBalance = resultingBalance;
        this.status = status;
    }

    public TransactionType getType() { return type; }
    public double getAmount() { return amount; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Double getResultingBalance() { return resultingBalance; }
    public TransactionStatus getStatus() { return status; }
}