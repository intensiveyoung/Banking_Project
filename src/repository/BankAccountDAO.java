package repository;

import domain.BankAccount;
import domain.DurationFilter;
import domain.Transaction;
import domain.TransactionType;

import java.time.LocalDateTime;
import java.util.List;

public interface BankAccountDAO {
    void saveAccount(BankAccount account);
    BankAccount findAccountByNumber(String accountNumber);
    void updateAccountBalance(String accountNumber, double newBalance);
    default void withdraw(String accountNumber, double amount, double fee) {
        BankAccount account = findAccountByNumber(accountNumber);
        if (account == null) {
            throw new IllegalArgumentException("Account number not found.");
        }
        int previousHistorySize = account.getTransactionHistory().size();
        account.withdrawWithFee(amount, fee);
        updateAccountBalance(accountNumber, account.getBalance());
        for (int index = previousHistorySize; index < account.getTransactionHistory().size(); index++) {
            logTransaction(accountNumber, account.getTransactionHistory().get(index));
        }
    }
    void transferFunds(String sourceAcc, String targetAcc, double amount);
    default void transferFunds(String sourceAcc, String targetAcc, double amount, double fee) {
        BankAccount source = findAccountByNumber(sourceAcc);
        if (source == null) {
            throw new IllegalArgumentException("Account number not found.");
        }
        double startingBalance = source.getBalance();
        double totalDebit = amount + fee;
        if (!Double.isFinite(totalDebit) || totalDebit > source.getEffectiveAvailable()) {
            throw new IllegalArgumentException("Insufficient funds including service fee.");
        }
        transferFunds(sourceAcc, targetAcc, amount);
        source.chargeServiceFee(fee);
        boolean overdraftFeeAssessed = startingBalance - totalDebit < 0.00;
        if (overdraftFeeAssessed) {
            source.chargeServiceFee(BankAccount.OVERDRAFT_FEE);
        }
        updateAccountBalance(sourceAcc, source.getBalance());
        int serviceFeeIndex = source.getTransactionHistory().size()
                - (overdraftFeeAssessed ? 2 : 1);
        logTransaction(sourceAcc, source.getTransactionHistory().get(serviceFeeIndex));
        if (overdraftFeeAssessed) {
            logTransaction(
                    sourceAcc,
                    source.getTransactionHistory().get(source.getTransactionHistory().size() - 1)
            );
        }
    }
    void updateAccountProfile(String accountNumber, String newName, Double newLimit);
    default void updateOverdraft(String accountNumber, boolean overdraftEnabled) {
        BankAccount account = findAccountByNumber(accountNumber);
        if (account == null) {
            throw new IllegalArgumentException("Account number not found.");
        }
        account.setOverdraftEnabled(overdraftEnabled);
    }
    void updateAccountSecurity(String accountNumber, String pinHash, String pinSalt,
                               String securityQuestion, String securityAnswerHash);
    void logTransaction(String accountNumber, Transaction transaction);
    List<Transaction> getTransactionHistory(String accountNumber);
    List<Transaction> getTransactionHistoryFiltered(String accountNumber, DurationFilter filter);
    List<Transaction> getTransactionHistoryFiltered(String accountNumber, TransactionType type,
                                                    DurationFilter filter, LocalDateTime customStart,
                                                    LocalDateTime customEnd);
    List<Transaction> getTransactionHistoryByDateRange(String accountNumber, LocalDateTime startDate,
                                                       LocalDateTime endDate);
    List<Transaction> getRecentTransactions(String accountNumber, int limit);
    void deleteAccountAndTransactions(String accountNumber);
    String getMaxAccountNumber();
}
