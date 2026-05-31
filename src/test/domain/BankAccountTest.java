package domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BankAccountTest {

    private BankAccount accountNoLimit;
    private BankAccount accountWithLimit;

    @BeforeEach
    void setUp() {
        accountNoLimit = new BankAccount("1001", "Alice", 100.00, null);
        accountWithLimit = new BankAccount("1002", "Bob", 100.00, 50.00);
    }

    @Test
    @DisplayName("Should create account with exactly $5.00 initial deposit")
    void testValidAccountCreation() {
        BankAccount validAcc = new BankAccount("1003", "Charlie", 5.00, null);
        assertEquals(5.00, validAcc.getBalance());
    }

    @Test
    @DisplayName("Should fail account creation if initial deposit is under $5.00")
    void testInvalidAccountCreation() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BankAccount("1003", "Charlie", 4.99, null);
        });
    }

    @Test
    @DisplayName("Should accept deposits of exactly or greater than" + BankAccount.MINIMUM_DEPOSIT)
    void testValidDeposit() {
        accountNoLimit.deposit(1.00);
        assertEquals(101.00, accountNoLimit.getBalance());
    }

    @Test
    @DisplayName("Should reject deposits under" + BankAccount.MINIMUM_DEPOSIT + "without mutating balance")
    void testInvalidDeposit() {
        assertThrows(IllegalArgumentException.class, () -> accountNoLimit.deposit(0.99));
        assertEquals(100.00, accountNoLimit.getBalance());
    }

    @Test
    @DisplayName("Should successfully withdraw within balance limits")
    void testSuccessfulWithdrawal() {
        accountNoLimit.withdraw(40.00);
        assertEquals(60.00, accountNoLimit.getBalance());
    }

    @Test
    @DisplayName("Should log a FAILED transaction and keep balance intact when overdrawing")
    void testOverdraftFailure() {
        assertThrows(InsufficientFundsException.class, () -> accountNoLimit.withdraw(150.00));
        assertEquals(100.00, accountNoLimit.getBalance());

        List<Transaction> history = accountNoLimit.getTransactionHistory();
        Transaction failedTx = history.get(history.size() - 1);
        assertEquals(TransactionStatus.FAILED, failedTx.getStatus());
        assertNull(failedTx.getResultingBalance());
    }

    @Test
    @DisplayName("Should respect daily limit constraints across multiple withdrawals")
    void testDailyLimitEnforcement() {
        accountWithLimit.withdraw(30.00);
        assertEquals(70.00, accountWithLimit.getBalance());

        assertThrows(DailyLimitExceededException.class, () -> accountWithLimit.withdraw(25.00));

        List<Transaction> history = accountWithLimit.getTransactionHistory();
        Transaction failedTx = history.get(history.size() - 1);
        assertEquals(TransactionStatus.FAILED, failedTx.getStatus());
    }

    @Test
    @DisplayName("Should maintain accurate daily limit verification across artificial time boundaries")
    void testDeterministicTimeDailyLimit() {
        // Creating a frozen clock snapshot at an exact instant (2026-05-31 at 12:00:00 UTC)
        java.time.Instant testingInstant = java.time.Instant.parse("2026-05-31T12:00:00Z");
        java.time.ZoneId utcZone = java.time.ZoneId.of("UTC");
        Clock frozenClock = Clock.fixed(testingInstant, utcZone);

        // Instantiating account with the frozen clock and a $50 daily withdrawal limit
        BankAccount timeLockedAccount = new BankAccount("9999", "TimeTraveler", 200.00, 50.00, frozenClock);

        // Making  valid withdrawal on this specific date and time
        timeLockedAccount.withdraw(30.00);
        assertEquals(170.00, timeLockedAccount.getBalance());

        // Attempting to breach the daily limit on the same frozen date boundary (Expecting failure)
        assertThrows(DailyLimitExceededException.class, () -> timeLockedAccount.withdraw(25.00));

        // Asserting the ledger timestamp matches our injected frozen date precisely
        List<Transaction> history = timeLockedAccount.getTransactionHistory();
        Transaction withdrawalTx = history.get(1); // Index 0 is initial deposit, Index 1 is the withdrawal

        LocalDateTime expectedTime = LocalDateTime.of(2026, 5, 31, 12, 0, 0);
        assertEquals(expectedTime, withdrawalTx.getTimestamp(), "The logged transaction timestamp must match the frozen mock clock exactly");
    }
}