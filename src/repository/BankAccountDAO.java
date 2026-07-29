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
    void updateAccountProfile(String accountNumber, String newName, Double newLimit);
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
