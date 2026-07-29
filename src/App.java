import domain.BankAccount;
import domain.DateUtil;
import domain.DurationFilter;
import domain.MoneyUtil;
import domain.SecurityQuestion;
import domain.Transaction;
import domain.TransactionStatus;
import service.BankingService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

public class App {
    private final BankingService bankingService;
    private final Scanner scanner;
    private final DateTimeFormatter timeFormatter;
    private final Clock clock;

    public App() {
        this(Clock.systemDefaultZone());
    }

    private App(Clock clock) {
        this(new BankingService(clock), clock);
    }

    App(BankingService bankingService) {
        this(bankingService, Clock.systemDefaultZone());
    }

    App(BankingService bankingService, Clock clock) {
        this.bankingService = bankingService;
        this.scanner = new Scanner(System.in);
        this.timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        this.clock = clock;
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
        System.out.println("6. Profile & Security Settings");
        System.out.println("=================================");

        String choice = getValidMenuChoice("Select an option (1-6): ");

        switch (choice) {
            case "1" -> handleDeposit();
            case "2" -> handleWithdrawal();
            case "3" -> handleCheckBalance();
            case "4" -> handleTransactionHistory();
            case "5" -> handleLogout();
            case "6" -> handleProfileSettings();
            default -> System.out.println("\n❌ Invalid choice! Please select an option between 1 and 6.");
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

        SecuritySetup securitySetup = promptForSecuritySetup("Create a 4-digit numeric PIN: ");

        String accountNum = bankingService.openAccount(
                name,
                initialDeposit,
                dailyLimit,
                securitySetup.pin(),
                securitySetup.question(),
                securitySetup.answer()
        );
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

        BankAccount account = bankingService.getAccountForAuthentication(accountNum);
        if (account.getPinHash() == null) {
            handleLegacySecurityEnrollment(account);
        } else {
            handleSecuredAccountLogin(account);
        }
        account = bankingService.getActiveAccount();
        System.out.println("\n✅ Login successful! Welcome back, " + account.getOwnerName() + "!");
    }

    private void handleLegacySecurityEnrollment(BankAccount account) {
        System.out.println("\nThis legacy account requires security enrollment.");
        System.out.print("Confirm account owner full name: ");
        String ownerName = scanner.nextLine().trim();
        bankingService.verifyLegacyOwner(account.getAccountNumber(), ownerName);
        SecuritySetup securitySetup = promptForSecuritySetup("Create a 4-digit numeric PIN: ");

        bankingService.enrollLegacyAccount(
                account.getAccountNumber(),
                ownerName,
                securitySetup.pin(),
                securitySetup.question(),
                securitySetup.answer()
        );
        bankingService.login(account.getAccountNumber(), securitySetup.pin());
        System.out.println("\n✅ Account security enrollment completed successfully!");
    }

    private void handleSecuredAccountLogin(BankAccount account) {
        System.out.print("Enter 4-digit PIN (or type 'RESET' to recover PIN): ");
        String pinOrReset = scanner.nextLine().trim();

        if (!pinOrReset.equalsIgnoreCase("RESET")) {
            bankingService.login(account.getAccountNumber(), pinOrReset);
            return;
        }

        System.out.println("Security question: " + account.getSecurityQuestion());
        System.out.print("Enter security answer: ");
        String securityAnswer = scanner.nextLine().trim();
        bankingService.verifySecurityAnswer(account.getAccountNumber(), securityAnswer);
        System.out.println("\n✅ Security answer verified successfully!");

        System.out.print("Enter a new 4-digit PIN: ");
        String newPin = scanner.nextLine().trim();
        bankingService.resetPin(account.getAccountNumber(), securityAnswer, newPin);
        bankingService.login(account.getAccountNumber(), newPin);
    }

    private SecuritySetup promptForSecuritySetup(String pinPrompt) {
        System.out.print(pinPrompt);
        String pin = scanner.nextLine().trim();

        System.out.println("Select a security question:");
        SecurityQuestion[] questions = SecurityQuestion.values();
        for (int index = 0; index < questions.length; index++) {
            System.out.println((index + 1) + ". " + questions[index].getText());
        }

        String selection = getValidMenuChoice("Select a question (1-5): ");
        SecurityQuestion question;
        try {
            question = SecurityQuestion.fromSelection(Integer.parseInt(selection));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Security question selection must be between 1 and 5.");
        }

        System.out.print("Enter security answer: ");
        String answer = scanner.nextLine().trim();
        return new SecuritySetup(pin, question.getText(), answer);
    }

    private record SecuritySetup(String pin, String question, String answer) {}

    private void handleProfileSettings() {
        boolean settingsOpen = true;
        while (settingsOpen) {
            System.out.println("\n========== PROFILE & SECURITY SETTINGS ==========");
            System.out.println("1. Update Display Name");
            System.out.println("2. Adjust Daily Withdrawal Limit");
            System.out.println("3. Change Security PIN");
            System.out.println("4. Back");
            System.out.println("=================================================");

            String choice = getValidMenuChoice("Select an option (1-4): ");
            switch (choice) {
                case "1" -> handleOwnerNameUpdate();
                case "2" -> handleDailyLimitUpdate();
                case "3" -> handlePinChange();
                case "4" -> settingsOpen = false;
                default -> System.out.println(
                        "\n❌ Invalid choice! Please select an option between 1 and 4."
                );
            }
        }
    }

    private void handleOwnerNameUpdate() {
        System.out.print("Enter new display name: ");
        String newName = scanner.nextLine().trim();
        if (bankingService.updateOwnerName(newName)) {
            System.out.println("\n✅ Display name updated successfully!");
        } else {
            System.out.println(
                    "\nℹ️ Display name is already set to '" + newName + "'. No changes were made."
            );
        }
    }

    private void handleDailyLimitUpdate() {
        System.out.print("Enter new daily withdrawal limit (or press Enter to remove limit): $");
        String limitInput = scanner.nextLine().trim();
        Double newLimit = null;
        if (!limitInput.isEmpty()) {
            try {
                newLimit = Double.parseDouble(limitInput);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid numeric input format entered.");
            }
        }

        if (!bankingService.updateDailyLimit(newLimit)) {
            if (newLimit == null) {
                System.out.println("\nℹ️ Daily withdrawal limit remains unchanged.");
            } else {
                System.out.println(
                        "\nℹ️ Daily withdrawal limit of "
                                + MoneyUtil.format(newLimit)
                                + " remains unchanged."
                );
            }
        } else if (newLimit == null) {
            System.out.println("\n✅ Daily withdrawal limit removed successfully!");
        } else {
            System.out.println(
                    "\n✅ Daily withdrawal limit updated to " + MoneyUtil.format(newLimit) + "!"
            );
        }
    }

    private void handlePinChange() {
        System.out.print("Enter current 4-digit PIN: ");
        String currentPin = scanner.nextLine().trim();
        System.out.print("Enter new 4-digit PIN: ");
        String newPin = scanner.nextLine().trim();

        bankingService.changePin(currentPin, newPin);
        System.out.println("\n✅ Security PIN changed successfully!");
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
        System.out.println("\n========== SELECT TIME DURATION ==========");
        System.out.println("1. Last 1 Week");
        System.out.println("2. Last 2 Weeks");
        System.out.println("3. Last 1 Month");
        System.out.println("4. Last 3 Months");
        System.out.println("5. Last 1 Year");
        System.out.println("6. Last 5 Years");
        System.out.println("7. All Time");
        System.out.println("8. Custom Last X Days");
        System.out.println("9. Custom Date Range (DD/MM/YYYY)");
        System.out.println("==========================================");

        String choice = getValidMenuChoice("Select an option (1-9): ");
        List<Transaction> history;
        try {
            history = switch (choice) {
                case "1", "2", "3", "4", "5", "6", "7" ->
                        bankingService.getTransactionHistory(
                                DurationFilter.fromSelection(Integer.parseInt(choice))
                        );
                case "8" -> getCustomLastDaysHistory();
                case "9" -> getCustomDateRangeHistory();
                default -> {
                    System.out.println(
                            "\n❌ Invalid choice! Please select an option between 1 and 9."
                    );
                    yield null;
                }
            };
        } catch (DateTimeParseException e) {
            System.out.println(
                    "\n⚠️ Invalid date format. Please use DD/MM/YYYY "
                            + "(e.g., 6/6/2026 or 06/06/2026)."
            );
            return;
        }

        if (history == null) {
            return;
        }
        if (history.isEmpty()) {
            System.out.println("\nℹ️ No transactions found for the selected time window.");
            return;
        }

        printTransactionHistory(history);
    }

    private List<Transaction> getCustomLastDaysHistory() {
        System.out.print("Enter number of days (e.g., 10): ");
        String daysInput = scanner.nextLine().trim();
        try {
            return bankingService.getTransactionHistoryForLastDays(Integer.parseInt(daysInput));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Number of days must be a positive integer.");
        }
    }

    private List<Transaction> getCustomDateRangeHistory() {
        System.out.print("Enter start date (DD/MM/YYYY): ");
        LocalDate startDate = DateUtil.parseFlexibleDate(scanner.nextLine());

        System.out.print("Enter end date (DD/MM/YYYY) [Press Enter for Today]: ");
        String endDateInput = scanner.nextLine().trim();
        LocalDate today = LocalDate.now(clock);
        LocalDate endDate = endDateInput.isEmpty()
                ? today
                : DateUtil.parseFlexibleDate(endDateInput);

        if (startDate.isAfter(today)) {
            throw new IllegalArgumentException("Start date cannot be in the future.");
        }
        if (endDate.isAfter(today)) {
            throw new IllegalArgumentException("End date cannot be in the future.");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.of(23, 59, 59));
        return bankingService.getTransactionHistoryByDateRange(startDateTime, endDateTime);
    }

    private void printTransactionHistory(List<Transaction> history) {
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
