package domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BankAccount {
    private final String accountNumber;
    private final String ownerName;
    private double balance;
    private final Double dailyWithdrawalLimit; // null means no limit
    private final List<Transaction> transactionHistory;
    private final Clock clock; // time source dependency
    public static final double MINIMUM_DEPOSIT = 1.00;
    public static final double INITIAL_MIN_DEPOSIT = 5.00;

    // Backwards compatibility constructor (defaults to real system time zone)
    public BankAccount(String accountNumber, String ownerName, double initialDeposit, Double dailyWithdrawalLimit) {
        this(accountNumber, ownerName, initialDeposit, dailyWithdrawalLimit, Clock.systemDefaultZone());
    }

    // Master dependency-injected constructor used for deterministic testing
    public BankAccount(String accountNumber, String ownerName, double initialDeposit, Double dailyWithdrawalLimit, Clock clock) {
        if (initialDeposit < BankAccount.INITIAL_MIN_DEPOSIT) {
            throw new IllegalArgumentException("Initial deposit must be at least " + MoneyUtil.format(BankAccount.INITIAL_MIN_DEPOSIT));
        }
        this.accountNumber = accountNumber;
        this.ownerName = ownerName;
        this.balance = initialDeposit;
        this.dailyWithdrawalLimit = dailyWithdrawalLimit;
        this.clock = clock;
        this.transactionHistory = new ArrayList<>();

        // Record initial deposit transaction
        this.transactionHistory.add(new Transaction(
                TransactionType.DEPOSIT, initialDeposit, LocalDateTime.now(this.clock), initialDeposit, TransactionStatus.SUCCESS
        ));
    }

    public synchronized void deposit(double amount) {
        if (amount < BankAccount.MINIMUM_DEPOSIT) {
            throw new IllegalArgumentException("Minimum deposit amount is " + MoneyUtil.format(BankAccount.MINIMUM_DEPOSIT));
        }
        balance += amount;
        transactionHistory.add(new Transaction(
                TransactionType.DEPOSIT, amount, LocalDateTime.now(clock), balance, TransactionStatus.SUCCESS
        ));
    }

    public synchronized void withdraw(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than 0");
        }

        // Rule Check 1: Insufficient Funds
        if (amount > balance) {
            transactionHistory.add(new Transaction(
                    TransactionType.WITHDRAWAL, amount, LocalDateTime.now(clock), null, TransactionStatus.FAILED
            ));
            throw new InsufficientFundsException("Insufficient funds for this withdrawal.");
        }

        // Rule Check 2: Daily Limit Validation
        if (dailyWithdrawalLimit != null) {
            double withdrawnToday = getWithdrawnAmountForDate(LocalDate.now(clock));
            if (withdrawnToday + amount > dailyWithdrawalLimit) {
                transactionHistory.add(new Transaction(
                        TransactionType.WITHDRAWAL, amount, LocalDateTime.now(clock), null, TransactionStatus.FAILED
                ));
                throw new DailyLimitExceededException("Daily withdrawal limit exceeded.");
            }
        }

        balance -= amount;
        transactionHistory.add(new Transaction(
                TransactionType.WITHDRAWAL, amount, LocalDateTime.now(clock), balance, TransactionStatus.SUCCESS
        ));
    }

    private double getWithdrawnAmountForDate(LocalDate date) {
        return transactionHistory.stream()
                .filter(t -> t.getType() == TransactionType.WITHDRAWAL)
                .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)
                .filter(t -> t.getTimestamp().toLocalDate().isEqual(date))
                .mapToDouble(Transaction::getAmount)
                .sum();
    }

    public String getAccountNumber() { return accountNumber; }
    public String getOwnerName() { return ownerName; }
    public double getBalance() { return balance; }
    public List<Transaction> getTransactionHistory() { return Collections.unmodifiableList(transactionHistory); }
    public Double getDailyWithdrawalLimit() { return dailyWithdrawalLimit; }
}