import domain.BankAccount;
import domain.MoneyUtil;
import domain.Transaction;
import domain.TransactionStatus;
import service.BankingService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

public class App {
    private final BankingService bankingService;
    private final Scanner scanner;
    private final DateTimeFormatter timeFormatter;

    public App() {
        this.bankingService = new BankingService();
        this.scanner = new Scanner(System.in);
        this.timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }

    public static void main(String[] args) {
        new App().runAppLoop();
    }

    public void runAppLoop() {
        System.out.println("=== WELCOME TO CORE BANKING SYSTEM ===");
        boolean running = true;

        while (running) {
            try {
                if (bankingService.getActiveAccount() == null) {
                    running = runUnauthenticatedMenu();
                } else {
                    running = runAuthenticatedMenu();
                }
            } catch (Exception e) {
                System.out.println("\n❌ ERROR: " + e.getMessage());
            }
        }
        scanner.close();
    }

    /**
     * STATE 1: Gateway Session Menu
     */
    private boolean runUnauthenticatedMenu() {
        System.out.println("\n---------------------------------");
        System.out.println("1. Open New Savings Account");
        System.out.println("2. Login to Existing Account");
        System.out.println("3. Exit System");
        System.out.println("---------------------------------");

        String choice = getValidMenuChoice("Select an option (1-3): ");

        switch (choice) {
            case "1" -> handleOpenAccount();
            case "2" -> handleLogin();
            case "3" -> {
                System.out.println("\nThank you for banking with us. Goodbye!");
                return false;
            }
            default -> System.out.println("\n❌ Invalid choice! Please select an option between 1 and 3.");
        }
        return true;
    }

    /**
     * STATE 2: Secure Session Menu
     */
    private boolean runAuthenticatedMenu() {
        BankAccount account = bankingService.getActiveAccount();
        System.out.println("\n=================================");
        System.out.println("👤 SESSION: " + account.getOwnerName() + " (" + account.getAccountNumber() + ")");
        System.out.println("---------------------------------");
        System.out.println("1. Deposit Funds");
        System.out.println("2. Withdraw Funds");
        System.out.println("3. Check Account Balance");
        System.out.println("4. View Transaction Ledger History");
        System.out.println("5. Logout");
        System.out.println("=================================");

        String choice = getValidMenuChoice("Select an option (1-5): ");

        switch (choice) {
            case "1" -> handleDeposit();
            case "2" -> handleWithdrawal();
            case "3" -> handleCheckBalance();
            case "4" -> handleTransactionHistory();
            case "5" -> handleLogout();
            default -> System.out.println("\n❌ Invalid choice! Please select an option between 1 and 5.");
        }
        return true;
    }

    /**
     * 🛡️ THE SYNCHRONOUS SHIELD
     * Forces the terminal to stay locked here until a non-empty choice is typed.
     * This naturally swallows accidental rapid double-enters.
     */
    private String getValidMenuChoice(String prompt) {
        while (true) {
            System.out.print(prompt);
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim();
                if (!input.isEmpty()) {
                    return input; // Return immediately once a real option is provided
                }
            }
        }
    }

    private void handleOpenAccount() {
        System.out.print("Enter account owner full name: ");
        String name = scanner.nextLine().trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Owner name cannot be empty.");

        System.out.print("Enter initial deposit amount (Min " + MoneyUtil.format(BankAccount.INITIAL_MIN_DEPOSIT) + "): $");
        double initialDeposit = readDoubleInput();

        System.out.print("Set an optional daily withdrawal limit (Or press Enter for no limit): $");
        String limitInput = scanner.nextLine().trim();

        Double dailyLimit;
        if (limitInput.isEmpty()) {
            dailyLimit = null;
        } else {
            try {
                dailyLimit = Double.parseDouble(limitInput);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid numeric input format entered.");
            }
        }

        String accountNum = bankingService.openAccount(name, initialDeposit, dailyLimit);
        System.out.println("\n✅ Account successfully created!");
        System.out.println("   Account Number: " + accountNum);
        System.out.println("   Account Holder: " + name);
    }

    private void handleLogin() {
        System.out.print("Enter your 4-digit account number to login: ");
        String accountNum = scanner.nextLine().trim();

        if (accountNum.isEmpty()) {
            throw new IllegalArgumentException("Account number cannot be empty.");
        }

        bankingService.login(accountNum);
        var account = bankingService.getActiveAccount();
        System.out.println("\n✅ Login successful! Welcome back, " + account.getOwnerName() + "!");
    }

    private void handleLogout() {
        bankingService.logout();
        System.out.println("\n✅ You have been securely signed out of your account.");
    }

    private void handleDeposit() {
        System.out.print("Enter deposit amount (Min " + MoneyUtil.format(BankAccount.MINIMUM_DEPOSIT) + "): $");
        double amount = readDoubleInput();
        bankingService.deposit(amount);
        System.out.printf("\n✅ Successfully deposited %s. New Balance: %s%n", MoneyUtil.format(amount), MoneyUtil.format(bankingService.checkBalance()));
    }

    private void handleWithdrawal() {
        System.out.print("Enter withdrawal amount: $");
        double amount = readDoubleInput();
        bankingService.withdraw(amount);
        System.out.printf("\n✅ Successfully withdrew %s. Remaining Balance: %s%n", MoneyUtil.format(amount), MoneyUtil.format(bankingService.checkBalance()));
    }

    private void handleCheckBalance() {
        double balance = bankingService.checkBalance();
        System.out.printf("\n💰 Current Active Balance: %s%n", MoneyUtil.format(balance));
    }

    private void handleTransactionHistory() {
        List<Transaction> history = bankingService.getHistory();
        System.out.println("\n=================== TRANSACTION AUDIT LEDGER ===================");
        System.out.printf("%-20s | %-12s | %-10s | %-15s | %-10s%n", "Timestamp", "Type", "Amount", "Result Balance", "Status");
        System.out.println("----------------------------------------------------------------");

        for (Transaction tx : history) {
            String balanceStr = (tx.getStatus() == TransactionStatus.FAILED) ? "N/A (Null)" : String.format("%s", MoneyUtil.format(tx.getResultingBalance()));
            System.out.printf("%-20s | %-12s | %-10s | %-15s | %-10s%n",
                    tx.getTimestamp().format(timeFormatter),
                    tx.getType(),
                    MoneyUtil.format(tx.getAmount()),
                    balanceStr,
                    tx.getStatus()
            );
        }
        System.out.println("================================================================");
    }

    private double readDoubleInput() {
        try {
            return Double.parseDouble(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric input format entered.");
        }
    }
}