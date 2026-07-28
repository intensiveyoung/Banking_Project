package service;

import domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BankingServiceIntegrationTest {

    private BankingService service;

    @BeforeEach

    void setUp() {
        AccountNumberGenerator.reset();
        service = new BankingService();
    }

    @Test
    @DisplayName("Integration Test: Complete User Banking Journey Flow")
    void testFullUserLifecycle() {
        // 1. Setup & Open Account (Verifying sequential baseline)
        String accNum = service.openAccount(
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
    @DisplayName("Integration Test: Verify Account Numbers increment sequentially across users")
    void testSequentialAccountIncrements() {
        BankingService service2 = new BankingService();

        String firstAcc = service.openAccount(
                "Bob",
                10.00,
                null,
                "1234",
                "What city were you born in?",
                "Nairobi"
        );
        String secondAcc = service2.openAccount(
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
    @DisplayName("Integration Test: Operations should fail if no account is active")
    void testStateProtectionWithoutAccount() {
        assertThrows(IllegalStateException.class, () -> service.deposit(50.00));
        assertThrows(IllegalStateException.class, () -> service.checkBalance());
    }
}
