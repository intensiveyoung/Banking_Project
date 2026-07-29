package service;

import domain.BankAccount;
import domain.AccountNumberGenerator;
import domain.DurationFilter;
import domain.SecurityQuestion;
import domain.SecurityUtil;
import domain.Transaction;
import domain.TransactionType;
import repository.BankAccountDAO;
import repository.PostgresBankAccountDAO;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class BankingService {
    private static final String SECURITY_HASH_SEPARATOR = ":";
    private final BankAccountDAO accountDAO;
    private final Clock clock;
    private String activeAccountNumber; // Keeps track of which user account number session is logged in

    public BankingService() {
        this(Clock.systemDefaultZone());
    }

    public BankingService(Clock clock) {
        this(new PostgresBankAccountDAO(clock), clock);
    }

    public BankingService(BankAccountDAO accountDAO) {
        this(accountDAO, Clock.systemDefaultZone());
    }

    public BankingService(BankAccountDAO accountDAO, Clock clock) {
        this.accountDAO = Objects.requireNonNull(accountDAO, "Account DAO is required.");
        this.clock = Objects.requireNonNull(clock, "Clock is required.");

        // DYNAMIC COUNTER INITIALIZATION:
        // Query the database for the highest active account index
        String maxAccountInDb = accountDAO.getMaxAccountNumber();
        if (maxAccountInDb != null) {
            int currentMax = Integer.parseInt(maxAccountInDb);
            // Re-assign the global generator baseline memory sequence value!
            AccountNumberGenerator.initializeCounter(currentMax);
        }
    }

    public String openAccount(String ownerName, double initialDeposit, Double dailyLimit, String pin,
                              String securityQuestion, String securityAnswer) {
        validateSecuritySetup(pin, securityQuestion, securityAnswer);

        String accNum = AccountNumberGenerator.getNextAccountNumber();
        BankAccount account = new BankAccount(accNum, ownerName, initialDeposit, dailyLimit, clock);
        String salt = SecurityUtil.generateSalt();
        account.setPinSalt(salt);
        account.setPinHash(SecurityUtil.hashPin(pin, salt));
        account.setSecurityQuestion(securityQuestion.trim());
        account.setSecurityAnswerHash(SecurityUtil.hashSecurityAnswer(securityAnswer, salt));

        accountDAO.saveAccount(account);
        this.activeAccountNumber = accNum;
        return accNum;
    }

    public void login(String accountNumber, String pin) {
        BankAccount account = accountDAO.findAccountByNumber(accountNumber);
        if (account == null || pin == null || !pin.matches("\\d{4}")
                || account.getPinHash() == null || account.getPinSalt() == null) {
            throw new IllegalArgumentException("Invalid account number or PIN entered.");
        }

        String enteredPinHash = SecurityUtil.hashPin(pin, account.getPinSalt());
        if (!enteredPinHash.equals(account.getPinHash())) {
            throw new IllegalArgumentException("Invalid account number or PIN entered.");
        }

        this.activeAccountNumber = accountNumber;
    }

    public BankAccount getAccountForAuthentication(String accountNumber) {
        BankAccount account = accountDAO.findAccountByNumber(accountNumber);
        if (account == null) {
            throw new IllegalArgumentException("Account number not found.");
        }
        return account;
    }

    public void enrollLegacyAccount(String accountNumber, String ownerName, String pin,
                                    String securityQuestion, String securityAnswer) {
        verifyLegacyOwner(accountNumber, ownerName);
        validateSecuritySetup(pin, securityQuestion, securityAnswer);
        persistSecurity(accountNumber, pin, securityQuestion.trim(), securityAnswer);
    }

    public void verifyLegacyOwner(String accountNumber, String ownerName) {
        BankAccount account = getAccountForAuthentication(accountNumber);
        if (account.getPinHash() != null) {
            throw new IllegalStateException("Account security is already configured.");
        }
        if (ownerName == null || !account.getOwnerName().equalsIgnoreCase(ownerName.trim())) {
            throw new IllegalArgumentException("Legacy account identity verification failed.");
        }
    }

    public void verifySecurityAnswer(String accountNumber, String securityAnswer) {
        BankAccount account = getAccountForAuthentication(accountNumber);
        if (account.getPinSalt() == null || account.getSecurityQuestion() == null
                || account.getSecurityAnswerHash() == null) {
            throw new IllegalStateException("Account security recovery is not configured.");
        }

        String answerSalt = account.getPinSalt();
        String expectedAnswerHash = account.getSecurityAnswerHash();
        int separatorIndex = expectedAnswerHash.indexOf(SECURITY_HASH_SEPARATOR);
        if (separatorIndex > 0) {
            answerSalt = expectedAnswerHash.substring(0, separatorIndex);
            expectedAnswerHash = expectedAnswerHash.substring(separatorIndex + 1);
        }

        String enteredAnswerHash = SecurityUtil.hashSecurityAnswer(securityAnswer, answerSalt);
        if (!enteredAnswerHash.equals(expectedAnswerHash)) {
            throw new IllegalArgumentException("Invalid security answer entered.");
        }
    }

    public void resetPin(String accountNumber, String securityAnswer, String newPin) {
        validatePin(newPin);
        BankAccount account = getAccountForAuthentication(accountNumber);
        verifySecurityAnswer(accountNumber, securityAnswer);
        persistSecurity(accountNumber, newPin, account.getSecurityQuestion(), securityAnswer);
    }

    private void persistSecurity(String accountNumber, String pin, String securityQuestion,
                                 String securityAnswer) {
        String salt = SecurityUtil.generateSalt();
        String pinHash = SecurityUtil.hashPin(pin, salt);
        String securityAnswerHash = SecurityUtil.hashSecurityAnswer(securityAnswer, salt);
        accountDAO.updateAccountSecurity(
                accountNumber,
                pinHash,
                salt,
                securityQuestion,
                securityAnswerHash
        );
    }

    private void validateSecuritySetup(String pin, String securityQuestion, String securityAnswer) {
        validatePin(pin);
        if (!SecurityQuestion.isSupported(securityQuestion)) {
            throw new IllegalArgumentException("A predefined security question must be selected.");
        }
        if (securityAnswer == null || securityAnswer.trim().isEmpty()) {
            throw new IllegalArgumentException("Security answer cannot be empty.");
        }
    }

    private void validatePin(String pin) {
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new IllegalArgumentException("PIN must contain exactly 4 numeric digits.");
        }
    }

    public boolean updateOwnerName(String newName) {
        ensureAccountSessionExists();
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Owner name cannot be empty.");
        }

        BankAccount account = getActiveAccount();
        String trimmedName = newName.trim();
        if (trimmedName.equals(account.getOwnerName())) {
            return false;
        }

        account.setOwnerName(trimmedName);
        accountDAO.updateAccountProfile(
                account.getAccountNumber(),
                account.getOwnerName(),
                account.getDailyWithdrawalLimit()
        );
        return true;
    }

    public boolean updateDailyLimit(Double newLimit) {
        ensureAccountSessionExists();
        if (newLimit != null && (!Double.isFinite(newLimit) || newLimit <= 0)) {
            throw new IllegalArgumentException("Daily withdrawal limit must be greater than $0.00.");
        }

        BankAccount account = getActiveAccount();
        Double currentLimit = account.getDailyWithdrawalLimit();
        boolean limitUnchanged = newLimit == null
                ? currentLimit == null
                : currentLimit != null && Double.compare(newLimit, currentLimit) == 0;
        if (limitUnchanged) {
            return false;
        }

        account.setDailyWithdrawalLimit(newLimit);
        accountDAO.updateAccountProfile(
                account.getAccountNumber(),
                account.getOwnerName(),
                account.getDailyWithdrawalLimit()
        );
        return true;
    }

    public void changePin(String currentPin, String newPin) {
        ensureAccountSessionExists();

        BankAccount account = getActiveAccount();
        if (currentPin == null || account.getPinHash() == null || account.getPinSalt() == null
                || !SecurityUtil.hashPin(currentPin, account.getPinSalt()).equals(account.getPinHash())) {
            throw new IllegalArgumentException("Current PIN is invalid.");
        }
        if (currentPin.equals(newPin)) {
            throw new IllegalArgumentException("New PIN cannot be identical to the current PIN.");
        }
        if (newPin == null || !newPin.matches("\\d{4}")) {
            throw new IllegalArgumentException("New PIN must be exactly 4 numeric digits.");
        }

        String recoveryAnswerHash = account.getSecurityAnswerHash();
        if (recoveryAnswerHash != null && !recoveryAnswerHash.contains(SECURITY_HASH_SEPARATOR)) {
            recoveryAnswerHash = account.getPinSalt() + SECURITY_HASH_SEPARATOR + recoveryAnswerHash;
        }

        String newSalt = SecurityUtil.generateSalt();
        String newPinHash = SecurityUtil.hashPin(newPin, newSalt);
        account.setPinHash(newPinHash);
        account.setPinSalt(newSalt);
        account.setSecurityAnswerHash(recoveryAnswerHash);
        accountDAO.updateAccountSecurity(
                account.getAccountNumber(),
                account.getPinHash(),
                account.getPinSalt(),
                account.getSecurityQuestion(),
                account.getSecurityAnswerHash()
        );
    }

    public void deposit(double amount) {
        ensureAccountSessionExists();
        BankAccount account = getActiveAccount();
        account.deposit(amount);

        // Push mutated local updates back to the persistent data tables
        accountDAO.updateAccountBalance(account.getAccountNumber(), account.getBalance());
        accountDAO.logTransaction(account.getAccountNumber(), account.getTransactionHistory().get(account.getTransactionHistory().size() - 1));
    }

    public void withdraw(double amount) {
        ensureAccountSessionExists();
        BankAccount account = getActiveAccount();

        // Crucial: Hydrate structural local daily limit context by pulling recent database logs
        // This ensures tracking limits functions correctly over consecutive separate run windows!
        List<Transaction> dbHistory = accountDAO.getTransactionHistory(account.getAccountNumber());
        // Hydrate the local object with missing database transactions
        for (int i = account.getTransactionHistory().size(); i < dbHistory.size(); i++) {
            Transaction historicalTx = dbHistory.get(i);
            account.hydrateTransaction(historicalTx); // Synchronizes the transient state!
        }

        try {
            account.withdraw(amount);
            accountDAO.updateAccountBalance(account.getAccountNumber(), account.getBalance());
        } finally {
            // Log transaction regardless of SUCCESS or FAILED state outcome per rules
            accountDAO.logTransaction(account.getAccountNumber(), account.getTransactionHistory().get(account.getTransactionHistory().size() - 1));
        }
    }

    public double checkBalance() {
        ensureAccountSessionExists();
        return getActiveAccount().getBalance();
    }

    public List<Transaction> getHistory() {
        return accountDAO.getTransactionHistory(activeAccountNumber);
    }

    public List<Transaction> getTransactionHistory(DurationFilter filter) {
        return getTransactionHistory(null, filter);
    }

    public List<Transaction> getTransactionHistory(TransactionType type, DurationFilter filter) {
        ensureAccountSessionExists();
        DurationFilter resolvedFilter = filter == null ? DurationFilter.ALL_TIME : filter;
        return accountDAO.getTransactionHistoryFiltered(
                activeAccountNumber,
                type,
                resolvedFilter,
                null,
                null
        );
    }

    public List<Transaction> getTransactionHistory(TransactionType type, DurationFilter filter,
                                                   LocalDateTime startDate,
                                                   LocalDateTime endDate) {
        ensureAccountSessionExists();
        validateHistoryDateRange(startDate, endDate);
        DurationFilter resolvedFilter = filter == null ? DurationFilter.ALL_TIME : filter;
        return accountDAO.getTransactionHistoryFiltered(
                activeAccountNumber,
                type,
                resolvedFilter,
                startDate,
                endDate
        );
    }

    public List<Transaction> getTransactionHistoryForLastDays(int days) {
        ensureAccountSessionExists();
        if (days <= 0) {
            throw new IllegalArgumentException("Number of days must be a positive integer.");
        }

        LocalDateTime endDate = LocalDateTime.now(clock);
        return accountDAO.getTransactionHistoryByDateRange(
                activeAccountNumber,
                endDate.minusDays(days),
                endDate
        );
    }

    public List<Transaction> getTransactionHistoryByDateRange(LocalDateTime startDate,
                                                              LocalDateTime endDate) {
        ensureAccountSessionExists();
        validateHistoryDateRange(startDate, endDate);
        LocalDateTime resolvedEndDate = endDate == null ? LocalDateTime.now(clock) : endDate;
        return accountDAO.getTransactionHistoryByDateRange(
                activeAccountNumber,
                startDate,
                resolvedEndDate
        );
    }

    public List<Transaction> getMiniStatement(int count) {
        ensureAccountSessionExists();
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Mini-statement transaction count must be greater than zero."
            );
        }
        return accountDAO.getRecentTransactions(activeAccountNumber, count);
    }

    private void validateHistoryDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime resolvedEndDate = endDate == null ? now : endDate;
        if (startDate != null && startDate.isAfter(now)) {
            throw new IllegalArgumentException("Start date cannot be in the future.");
        }
        if (startDate != null && startDate.isAfter(resolvedEndDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }
    }

    public BankAccount getActiveAccount() {
        if (activeAccountNumber == null) {
            return null; // Return null safely if no session is active yet
        }
        return accountDAO.findAccountByNumber(activeAccountNumber);
    }

    // Keep the explicit guardrail check separate for financial mutations
    private void ensureAccountSessionExists() {
        if (activeAccountNumber == null) {
            throw new IllegalStateException("No active account session found. Please open an account or login first.");
        }
    }

    public void logout() {
        this.activeAccountNumber = null; // Purges session footprint from memory
    }
}
