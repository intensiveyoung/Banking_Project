package service;

import domain.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import repository.PostgresBankAccountDAO;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class BankingServiceIntegrationTest {

    private static final Set<String> ALL_CREATED_TEST_ACCOUNT_NUMBERS =
            ConcurrentHashMap.newKeySet();
    private final Set<String> createdTestAccountNumbers = new HashSet<>();
    private BankingService service;
    private PostgresBankAccountDAO cleanupDAO;

    @BeforeEach
    void setUp() {
        AccountNumberGenerator.reset();
        service = new BankingService();
        cleanupDAO = new PostgresBankAccountDAO();
    }

    @AfterEach
    void tearDownTestAccounts() {
        RuntimeException cleanupFailure = null;
        for (String accountNumber : createdTestAccountNumbers) {
            try {
                cleanupDAO.deleteAccountAndTransactions(accountNumber);
            } catch (RuntimeException e) {
                if (cleanupFailure == null) {
                    cleanupFailure = new RuntimeException("Integration test data cleanup failed.");
                }
                cleanupFailure.addSuppressed(e);
            }
        }
        createdTestAccountNumbers.clear();

        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    @AfterAll
    static void verifyTestAccountsWereRemoved() {
        PostgresBankAccountDAO verificationDAO = new PostgresBankAccountDAO();
        for (String accountNumber : ALL_CREATED_TEST_ACCOUNT_NUMBERS) {
            assertNull(
                    verificationDAO.findAccountByNumber(accountNumber),
                    "Test account should be deleted: " + accountNumber
            );
            assertTrue(
                    verificationDAO.getTransactionHistory(accountNumber).isEmpty(),
                    "Test transactions should be deleted: " + accountNumber
            );
        }
    }

    private String openTrackedAccount(BankingService targetService, String ownerName,
                                      double initialDeposit, Double dailyLimit, String pin,
                                      String securityQuestion, String securityAnswer) {
        String accountNumber = targetService.openAccount(
                ownerName,
                initialDeposit,
                dailyLimit,
                pin,
                securityQuestion,
                securityAnswer
        );
        createdTestAccountNumbers.add(accountNumber);
        ALL_CREATED_TEST_ACCOUNT_NUMBERS.add(accountNumber);
        return accountNumber;
    }

    @Test
    @DisplayName("Integration Test: Complete User Banking Journey Flow")
    void testFullUserLifecycle() {
        // 1. Setup & Open Account (Verifying sequential baseline)
        String accNum = openTrackedAccount(
                service,
                "Alice",
                100.00,
                50.00,
                "1234",
                "What is your first pet's name?",
                "Milo"
        );
        assertNotNull(accNum, "Account number should be generated");
        assertTrue(accNum.matches("\\d{4,}"), "Account number should be a numeric sequence value");
        assertEquals(100.00, service.checkBalance());

        BankAccount securedAccount = service.getActiveAccount();
        assertNotEquals("1234", securedAccount.getPinHash());
        assertNotNull(securedAccount.getPinSalt());
        assertEquals("What is your first pet's name?", securedAccount.getSecurityQuestion());
        assertNotEquals("Milo", securedAccount.getSecurityAnswerHash());

        service.logout();
        IllegalArgumentException loginError = assertThrows(
                IllegalArgumentException.class,
                () -> service.login(accNum, "9999")
        );
        assertEquals("Invalid account number or PIN entered.", loginError.getMessage());
        assertNull(service.getActiveAccount());

        service.login(accNum, "1234");
        assertEquals(accNum, service.getActiveAccount().getAccountNumber());

        service.logout();
        service.verifySecurityAnswer(accNum, "  MILO  ");
        service.resetPin(accNum, "  MILO  ", "4321");
        assertThrows(IllegalArgumentException.class, () -> service.login(accNum, "1234"));
        service.login(accNum, "4321");
        assertEquals(accNum, service.getActiveAccount().getAccountNumber());

        // 2. Perform a successful valid deposit (Verifying new $1.00 requirement from BANK-14)
        service.deposit(1.00);
        assertEquals(101.00, service.checkBalance());

        // 3. Perform a successful withdrawal within daily limits
        service.withdraw(30.00);
        assertEquals(71.00, service.checkBalance());

        // 4. Trigger a failure due to breaching daily limits ($30 successful + $25 attempted > $50 limit)
        assertThrows(DailyLimitExceededException.class, () -> service.withdraw(25.00));
        assertEquals(71.00, service.checkBalance(), "Balance must remain unchanged after a failed withdrawal");

        // 5. Verify the state of the transaction history ledger
        List<Transaction> history = service.getHistory();

        // Expected ledger count: 1 Initial Deposit + 1 Manual Deposit + 1 Success Withdraw + 1 Failed Withdraw = 4
        assertEquals(4, history.size());

        // Verify the final transaction logged is the FAILED one with a null balance pointer
        Transaction failedTx = history.get(history.size() - 1);
        assertEquals(TransactionType.WITHDRAWAL, failedTx.getType());
        assertEquals(TransactionStatus.FAILED, failedTx.getStatus());
        assertNull(failedTx.getResultingBalance(), "Failed transactions must show null balance per requirements");
    }

    @Test
    @DisplayName("Integration Test: PostgreSQL transfer rolls back when the transaction fails")
    void transferRollbackLeavesPostgresAccountsUnchanged() {
        String sourceAccountNumber = openTrackedAccount(
                service, "Rollback Source", 100.00, null, "1234",
                SecurityQuestion.FIRST_PET.getText(), "Milo"
        );
        String targetAccountNumber = openTrackedAccount(
                service, "Rollback Target", 50.00, null, "4321",
                SecurityQuestion.FAVORITE_BOOK.getText(), "Dune"
        );

        assertThrows(RuntimeException.class,
                () -> cleanupDAO.transferFunds(sourceAccountNumber, targetAccountNumber,
                        Double.POSITIVE_INFINITY));

        assertEquals(100.00, cleanupDAO.findAccountByNumber(sourceAccountNumber).getBalance());
        assertEquals(50.00, cleanupDAO.findAccountByNumber(targetAccountNumber).getBalance());
        assertEquals(1, cleanupDAO.getTransactionHistory(sourceAccountNumber).size());
        assertEquals(1, cleanupDAO.getTransactionHistory(targetAccountNumber).size());
    }

    @Test
    @DisplayName("Integration Test: Verify Account Numbers increment sequentially across users")
    void testSequentialAccountIncrements() {
        BankingService service2 = new BankingService();

        String firstAcc = openTrackedAccount(
                service,
                "Bob",
                10.00,
                null,
                "1234",
                "What city were you born in?",
                "Nairobi"
        );
        String secondAcc = openTrackedAccount(
                service2,
                "Charlie",
                20.00,
                null,
                "5678",
                "What was your first school?",
                "Central"
        );

        int firstNum = Integer.parseInt(firstAcc);
        int secondNum = Integer.parseInt(secondAcc);

        assertEquals(1, secondNum - firstNum, "Account numbers must be strictly sequential");
    }

    @Test
    @DisplayName("Integration Test: Profile updates and PIN changes persist")
    void testProfileAndSecurityUpdatesPersist() {
        String accountNumber = openTrackedAccount(
                service,
                "Original Name",
                100.00,
                null,
                "1234",
                SecurityQuestion.FIRST_PET.getText(),
                "Milo"
        );

        service.updateOwnerName("Updated Name");
        service.updateDailyLimit(80.00);

        BankAccount updatedAccount = service.getActiveAccount();
        assertEquals("Updated Name", updatedAccount.getOwnerName());
        assertEquals(80.00, updatedAccount.getDailyWithdrawalLimit());

        service.updateDailyLimit(null);
        assertNull(service.getActiveAccount().getDailyWithdrawalLimit());

        service.changePin("1234", "5678");
        service.logout();
        assertThrows(IllegalArgumentException.class, () -> service.login(accountNumber, "1234"));
        service.login(accountNumber, "5678");
        assertEquals(accountNumber, service.getActiveAccount().getAccountNumber());
        assertDoesNotThrow(() -> service.verifySecurityAnswer(accountNumber, "Milo"));
    }

    @Test
    @DisplayName("Integration Test: Transaction history duration queries execute against PostgreSQL")
    void testTransactionHistoryDurationQueries() {
        openTrackedAccount(
                service,
                "History User",
                100.00,
                null,
                "1234",
                SecurityQuestion.FIRST_PET.getText(),
                "Milo"
        );
        service.deposit(10.00);

        List<Transaction> presetHistory = service.getTransactionHistory(DurationFilter.ONE_WEEK);
        List<Transaction> customDaysHistory = service.getTransactionHistoryForLastDays(10);
        LocalDateTime now = LocalDateTime.now();
        List<Transaction> dateRangeHistory = service.getTransactionHistoryByDateRange(
                now.minusDays(1),
                now.plusSeconds(1)
        );

        assertEquals(2, presetHistory.size());
        assertEquals(2, customDaysHistory.size());
        assertEquals(2, dateRangeHistory.size());
        assertFalse(presetHistory.get(0).getTimestamp().isBefore(
                presetHistory.get(1).getTimestamp()
        ));
    }

    @Test
    @DisplayName("Integration Test: Transaction type filters execute against PostgreSQL")
    void testTransactionTypeFilters() {
        String accountNumber = openTrackedAccount(
                service,
                "Type Filter User",
                100.00,
                null,
                "1234",
                SecurityQuestion.FIRST_PET.getText(),
                "Milo"
        );
        service.deposit(10.00);
        service.withdraw(5.00);
        cleanupDAO.logTransaction(
                accountNumber,
                new Transaction(
                        TransactionType.TRANSFER,
                        3.00,
                        LocalDateTime.now(),
                        service.checkBalance(),
                        TransactionStatus.SUCCESS
                )
        );

        List<Transaction> deposits = service.getTransactionHistory(
                TransactionType.DEPOSIT,
                DurationFilter.ALL_TIME
        );
        List<Transaction> withdrawals = service.getTransactionHistory(
                TransactionType.WITHDRAWAL,
                DurationFilter.ALL_TIME
        );
        List<Transaction> transfers = service.getTransactionHistory(
                TransactionType.TRANSFER,
                DurationFilter.ALL_TIME
        );

        assertEquals(2, deposits.size());
        assertEquals(1, withdrawals.size());
        assertEquals(1, transfers.size());
        assertTrue(deposits.stream().allMatch(
                transaction -> transaction.getType() == TransactionType.DEPOSIT
        ));
        assertTrue(withdrawals.stream().allMatch(
                transaction -> transaction.getType() == TransactionType.WITHDRAWAL
        ));
        assertTrue(transfers.stream().allMatch(
                transaction -> transaction.getType() == TransactionType.TRANSFER
        ));
    }

    @Test
    @DisplayName("Integration Test: Mini-statement limits recent PostgreSQL transactions")
    void testMiniStatementLimitsRecentTransactions() {
        String accountNumber = openTrackedAccount(
                service,
                "Mini Statement User",
                100.00,
                null,
                "1234",
                SecurityQuestion.FIRST_PET.getText(),
                "Milo"
        );
        LocalDateTime startingTimestamp = LocalDateTime.now();
        for (int index = 1; index <= 5; index++) {
            cleanupDAO.logTransaction(
                    accountNumber,
                    new Transaction(
                            TransactionType.DEPOSIT,
                            300.00 + index,
                            startingTimestamp.plusSeconds(index),
                            100.00 + index,
                            TransactionStatus.SUCCESS
                    )
            );
        }

        List<Transaction> miniStatement = service.getMiniStatement(3);

        assertEquals(3, miniStatement.size());
        assertEquals(305.00, miniStatement.get(0).getAmount());
        assertEquals(304.00, miniStatement.get(1).getAmount());
        assertEquals(303.00, miniStatement.get(2).getAmount());
    }

    @Test
    @DisplayName("Integration Test: Operations should fail if no account is active")
    void testStateProtectionWithoutAccount() {
        assertThrows(IllegalStateException.class, () -> service.deposit(50.00));
        assertThrows(IllegalStateException.class, () -> service.checkBalance());
    }
}
