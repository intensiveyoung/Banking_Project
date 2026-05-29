package service;

import domain.BankAccount;
import domain.AccountNumberGenerator;
import domain.Transaction;
import java.util.List;

public class BankingService {
    private BankAccount activeAccount;

    /**
     * Opens a new basic savings account in the system.
     * @return The unique account number generated for this account.
     */
    public String openAccount(String ownerName, double initialDeposit, Double dailyLimit) {
        String accNum = AccountNumberGenerator.getNextAccountNumber();
        this.activeAccount = new BankAccount(accNum, ownerName, initialDeposit, dailyLimit);
        return accNum;
    }

    public void deposit(double amount) {
        ensureAccountExists();
        activeAccount.deposit(amount);
    }

    public void withdraw(double amount) {
        ensureAccountExists();
        activeAccount.withdraw(amount);
    }

    public double checkBalance() {
        ensureAccountExists();
        return activeAccount.getBalance();
    }

    public List<Transaction> getHistory() {
        ensureAccountExists();
        return activeAccount.getTransactionHistory();
    }

    public BankAccount getActiveAccount() {
        return this.activeAccount;
    }

    private void ensureAccountExists() {
        if (activeAccount == null) {
            throw new IllegalStateException("No active account found. Please open an account first.");
        }
    }
}