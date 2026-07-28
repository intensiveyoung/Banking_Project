package repository;

import domain.BankAccount;
import domain.Transaction;
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
    String getMaxAccountNumber();
}
