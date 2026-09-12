package domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BankAccount {
    private final String accountNumber;
    private String ownerName;
    private double balance;
    private Double dailyWithdrawalLimit; // null means no limit
    private final List<Transaction> transactionHistory;
    private final Clock clock; // time source dependency
    private String pinHash;
    private String pinSalt;
    private String securityQuestion;
    private String securityAnswerHash;
    private boolean overdraftEnabled;
    private double overdraftLimit;
    public static final double MINIMUM_DEPOSIT = 1.00;
    public static final double INITIAL_MIN_DEPOSIT = 5.00;
    public static final double MINIMUM_WITHDRAWAL = 1.00;
    public static final double DEFAULT_OVERDRAFT_LIMIT = 500.00;
    public static final double OVERDRAFT_FEE = 35.00;

    // Backwards compatibility constructor (defaults to real system time zone)
    public BankAccount(String accountNumber, String ownerName, double initialDeposit, Double dailyWithdrawalLimit) {
        this(accountNumber, ownerName, initialDeposit, dailyWithdrawalLimit, Clock.systemDefaultZone());
    }

    // Master dependency-injected constructor used for deterministic testing
    public BankAccount(String accountNumber, String ownerName, double initialDeposit, Double dailyWithdrawalLimit, Clock clock) {
        this(accountNumber, ownerName, initialDeposit, dailyWithdrawalLimit, clock, true);
    }

    private BankAccount(String accountNumber, String ownerName, double balance, Double dailyWithdrawalLimit,
                        Clock clock, boolean recordInitialDeposit) {
        if (recordInitialDeposit && balance < BankAccount.INITIAL_MIN_DEPOSIT) {
            throw new IllegalArgumentException("Initial deposit must be at least " + MoneyUtil.format(INITIAL_MIN_DEPOSIT));
        }
        this.accountNumber = accountNumber;
        this.ownerName = ownerName;
        this.balance = balance;
        this.dailyWithdrawalLimit = dailyWithdrawalLimit;
        this.clock = clock;
        this.transactionHistory = new ArrayList<>();
        this.overdraftEnabled = false;
        this.overdraftLimit = DEFAULT_OVERDRAFT_LIMIT;

        if (recordInitialDeposit) {
            this.transactionHistory.add(new Transaction(
                    TransactionType.DEPOSIT,
                    balance,
                    LocalDateTime.now(this.clock),
                    balance,
                    TransactionStatus.SUCCESS
            ));
        }
    }

    public static BankAccount rehydrate(String accountNumber, String ownerName, double balance,
                                        Double dailyWithdrawalLimit) {
        return rehydrate(accountNumber, ownerName, balance, dailyWithdrawalLimit,
                false, DEFAULT_OVERDRAFT_LIMIT);
    }

    public static BankAccount rehydrate(String accountNumber, String ownerName, double balance,
                                        Double dailyWithdrawalLimit, boolean overdraftEnabled,
                                        double overdraftLimit) {
        BankAccount account = new BankAccount(
                accountNumber,
                ownerName,
                balance,
                dailyWithdrawalLimit,
                Clock.systemDefaultZone(),
                false
        );
        account.setOverdraftEnabled(overdraftEnabled);
        account.setOverdraftLimit(overdraftLimit);
        return account;
    }

    public synchronized void deposit(double amount) {
        if (amount < BankAccount.MINIMUM_DEPOSIT) {
            throw new IllegalArgumentException("Minimum deposit amount is " + MoneyUtil.format(MINIMUM_DEPOSIT));
        }
        balance += amount;
        transactionHistory.add(new Transaction(
                TransactionType.DEPOSIT, amount, LocalDateTime.now(clock), balance, TransactionStatus.SUCCESS
        ));
    }

    public synchronized void withdraw(double amount) {
        if (amount <= MINIMUM_WITHDRAWAL) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than " + MoneyUtil.format(MINIMUM_WITHDRAWAL));
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

    public synchronized void withdrawWithFee(double amount, double fee) {
        double totalDebit = amount + fee;
        if (amount <= MINIMUM_WITHDRAWAL) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than " + MoneyUtil.format(MINIMUM_WITHDRAWAL));
        }
        if (totalDebit > getEffectiveAvailable()) {
            throw new IllegalArgumentException("Insufficient funds including service fee.");
        }
        double overdraftFee = balance - totalDebit < 0.00 ? OVERDRAFT_FEE : 0.00;
        if (dailyWithdrawalLimit != null
                && getOutgoingAmountForDate(LocalDate.now(clock)) + totalDebit + overdraftFee
                > dailyWithdrawalLimit) {
            throw new DailyLimitExceededException("Daily withdrawal limit exceeded.");
        }

        balance -= amount;
        transactionHistory.add(new Transaction(
                TransactionType.WITHDRAWAL, amount, LocalDateTime.now(clock), balance,
                TransactionStatus.SUCCESS
        ));
        chargeServiceFee(fee);
        if (overdraftFee > 0.00) {
            chargeServiceFee(overdraftFee);
        }
    }

    public synchronized void chargeServiceFee(double fee) {
        balance -= fee;
        transactionHistory.add(new Transaction(
                TransactionType.SERVICE_FEE, fee, LocalDateTime.now(clock), balance,
                TransactionStatus.SUCCESS
        ));
    }

    public synchronized void transferOut(double amount) {
        validateTransferAmount(amount);
        if (amount > getEffectiveAvailable()) {
            throw new InsufficientFundsException("Insufficient funds for this transfer.");
        }
        if (dailyWithdrawalLimit != null
                && getOutgoingAmountForDate(LocalDate.now(clock)) + amount > dailyWithdrawalLimit) {
            throw new DailyLimitExceededException("Daily withdrawal limit exceeded.");
        }
        balance -= amount;
        transactionHistory.add(new Transaction(TransactionType.TRANSFER_OUT, amount,
                LocalDateTime.now(clock), balance, TransactionStatus.SUCCESS));
    }

    public synchronized void transferIn(double amount) {
        validateTransferAmount(amount);
        balance += amount;
        transactionHistory.add(new Transaction(TransactionType.TRANSFER_IN, amount,
                LocalDateTime.now(clock), balance, TransactionStatus.SUCCESS));
    }

    private double getWithdrawnAmountForDate(LocalDate date) {
        return transactionHistory.stream()
                .filter(t -> t.getType() == TransactionType.WITHDRAWAL)
                .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)
                .filter(t -> t.getTimestamp().toLocalDate().isEqual(date))
                .mapToDouble(Transaction::getAmount)
                .sum();
    }

    private double getOutgoingAmountForDate(LocalDate date) {
        return transactionHistory.stream()
                .filter(t -> t.getType() == TransactionType.WITHDRAWAL
                        || t.getType() == TransactionType.TRANSFER_OUT
                        || t.getType() == TransactionType.SERVICE_FEE)
                .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)
                .filter(t -> t.getTimestamp().toLocalDate().isEqual(date))
                .mapToDouble(Transaction::getAmount)
                .sum();
    }

    private void validateTransferAmount(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than $0.00.");
        }
    }

    public String getAccountNumber() { return accountNumber; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public double getBalance() { return balance; }
    public List<Transaction> getTransactionHistory() { return Collections.unmodifiableList(transactionHistory); }
    public Double getDailyWithdrawalLimit() { return dailyWithdrawalLimit; }
    public void setDailyWithdrawalLimit(Double dailyWithdrawalLimit) { this.dailyWithdrawalLimit = dailyWithdrawalLimit; }
    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }
    public String getPinSalt() { return pinSalt; }
    public void setPinSalt(String pinSalt) { this.pinSalt = pinSalt; }
    public String getSecurityQuestion() { return securityQuestion; }
    public void setSecurityQuestion(String securityQuestion) { this.securityQuestion = securityQuestion; }
    public String getSecurityAnswerHash() { return securityAnswerHash; }
    public void setSecurityAnswerHash(String securityAnswerHash) { this.securityAnswerHash = securityAnswerHash; }
    public boolean isOverdraftEnabled() { return overdraftEnabled; }
    public void setOverdraftEnabled(boolean overdraftEnabled) { this.overdraftEnabled = overdraftEnabled; }
    public double getOverdraftLimit() { return overdraftLimit; }
    public void setOverdraftLimit(double overdraftLimit) {
        if (!Double.isFinite(overdraftLimit) || overdraftLimit < 0.00) {
            throw new IllegalArgumentException("Overdraft limit cannot be negative.");
        }
        this.overdraftLimit = overdraftLimit;
    }
    public double getEffectiveAvailable() {
        return balance + (overdraftEnabled ? overdraftLimit : 0.00);
    }

    public synchronized void hydrateTransaction(Transaction tx) {
        // Directly appends a historical transaction record from the DB without executing mutations
        this.transactionHistory.add(tx);
    }
}
