package service;

import domain.BankAccount;
import domain.Transaction;
import repository.BankAccountDAO;
import repository.PostgresBankAccountDAO;
import java.util.List;

public class BankingService {
    private final BankAccountDAO accountDAO;
    private String activeAccountNumber; // Keeps track of which user account number session is logged in

    public BankingService() {
        this.accountDAO = new PostgresBankAccountDAO();

        // DYNAMIC COUNTER INITIALIZATION:
        // Query the database for the highest active account index
        String maxAccountInDb = accountDAO.getMaxAccountNumber();
        if (maxAccountInDb != null) {
            int currentMax = Integer.parseInt(maxAccountInDb);
            // Re-assign the global generator baseline memory sequence value!
            domain.AccountNumberGenerator.initializeCounter(currentMax);
        }
    }

    public String openAccount(String ownerName, double initialDeposit, Double dailyLimit) {
        String accNum = domain.AccountNumberGenerator.getNextAccountNumber();
        BankAccount account = new BankAccount(accNum, ownerName, initialDeposit, dailyLimit);

        accountDAO.saveAccount(account);
        this.activeAccountNumber = accNum;
        return accNum;
    }

    public void login(String accountNumber) {
        BankAccount account = accountDAO.findAccountByNumber(accountNumber);
        if (account == null) {
            throw new IllegalArgumentException("Account number not found.");
        }
        this.activeAccountNumber = accountNumber;
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