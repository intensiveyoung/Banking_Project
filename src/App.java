import domain.AccountNumberGenerator;
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
        System.out.println("=== WELCOME TO SPRINT-1 CORE BANKING SYSTEM ===");
        boolean running = true;

        while (running) {
            printMenu();
            System.out.print("Select an option (1-6): ");
            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> handleOpenAccount();
                    case "2" -> handleDeposit();
                    case "3" -> handleWithdrawal();
                    case "4" -> handleCheckBalance();
                    case "5" -> handleTransactionHistory();
                    case "6" -> {
                        System.out.println("\nThank you for banking with us. Goodbye!");
                        AccountNumberGenerator.reset();
                        running = false;
                    }
                    default -> System.out.println("\n❌ Invalid choice! Please select an option between 1 and 6.");
                }
            } catch (Exception e) {
                // Trap domain exceptions cleanly to protect application runtime state
                System.out.println("\n❌ ERROR: " + e.getMessage());
            }
        }
        scanner.close();
    }

    private void printMenu() {
        System.out.println("\n---------------------------------");
        System.out.println("1. Open New Savings Account");
        System.out.println("2. Deposit Funds");
        System.out.println("3. Withdraw Funds");
        System.out.println("4. Check Account Balance");
        System.out.println("5. View Transaction Ledger History");
        System.out.println("6. Exit System");
        System.out.println("---------------------------------");
    }

    private void handleOpenAccount() {
        if (bankingService.getActiveAccount() != null) {
            System.out.println("\n❌ An active account already exists for this session (V1 Limit).");
            return;
        }

        System.out.print("Enter account owner full name: ");
        String name = scanner.nextLine().trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Owner name cannot be empty.");

        System.out.print("Enter initial deposit amount (Min $5.00): $");
        double initialDeposit = readDoubleInput();

        System.out.print("Set an optional daily withdrawal limit (Or press Enter for no limit): $");
        String limitInput = scanner.nextLine().trim();
        Double dailyLimit = limitInput.isEmpty() ? null : Double.parseDouble(limitInput);

        String accountNum = bankingService.openAccount(name, initialDeposit, dailyLimit);
        System.out.println("\n✅ Account successfully created!");
        System.out.println("   Account Number: " + accountNum);
        System.out.println("   Account Holder: " + name);
    }

    private void handleDeposit() {
        System.out.print("Enter deposit amount (Min $1.00): $");
        double amount = readDoubleInput();
        bankingService.deposit(amount);
        System.out.printf("\n✅ Successfully deposited $%.2f. New Balance: $%.2f%n", amount, bankingService.checkBalance());
    }

    private void handleWithdrawal() {
        System.out.print("Enter withdrawal amount: $");
        double amount = readDoubleInput();
        bankingService.withdraw(amount);
        System.out.printf("\n✅ Successfully withdrew $%.2f. Remaining Balance: $%.2f%n", amount, bankingService.checkBalance());
    }

    private void handleCheckBalance() {
        double balance = bankingService.checkBalance();
        System.out.printf("\n💰 Current Active Balance: $%.2f%n", balance);
    }

    private void handleTransactionHistory() {
        List<Transaction> history = bankingService.getHistory();
        System.out.println("\n=================== TRANSACTION AUDIT LEDGER ===================");
        System.out.printf("%-20s | %-12s | %-10s | %-15s | %-10s%n", "Timestamp", "Type", "Amount", "Result Balance", "Status");
        System.out.println("----------------------------------------------------------------");

        for (Transaction tx : history) {
            String balanceStr = (tx.getStatus() == TransactionStatus.FAILED) ? "N/A (Null)" : String.format("$%.2f", tx.getResultingBalance());
            System.out.printf("%-20s | %-12s | $%-9.2f | %-15s | %-10s%n",
                    tx.getTimestamp().format(timeFormatter),
                    tx.getType(),
                    tx.getAmount(),
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