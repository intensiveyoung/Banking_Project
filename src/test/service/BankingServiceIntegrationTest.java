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
        service = new BankingService();
    }

    @Test
    @DisplayName("Integration Test: Complete User BankAccountTest.java Journey Flow")
    void testFullUserLifecycle() {
        // 1. Setup & Open Account Sequentially
        String accNum1 = service.openAccount("Alice", 100.00, 50.00);
        assertEquals("1001", accNum1, "First account should be 1001");
        assertEquals(100.00, service.checkBalance());

        // 2. Perform a successful valid deposit
        service.deposit(50.00); 
        assertEquals(150.00, service.checkBalance());

        // 3. Perform a successful withdrawal within daily limits
        service.withdraw(30.00);
        assertEquals(120.00, service.checkBalance());

        // 4. Trigger a failure due to breaching daily limits ($30 + $25 > $50)
        assertThrows(DailyLimitExceededException.class, () -> service.withdraw(25.00));
        assertEquals(120.00, service.checkBalance(), "Balance must remain unchanged after failure");

        // 5. Verify the state of the transaction history ledger
        List<Transaction> history = service.getHistory();
        
        // We expect exactly 4 ledger records: Initial deposit, Deposit, Success Withdraw, Failed Withdraw
        assertEquals(4, history.size());

        // Assert structural rules established in our backlog architecture
        Transaction initialTx = history.get(0);
        assertEquals(TransactionType.DEPOSIT, initialTx.getType());
        assertEquals(100.00, initialTx.getResultingBalance());

        Transaction failedTx = history.get(3);
        assertEquals(TransactionType.WITHDRAWAL, failedTx.getType());
        assertEquals(TransactionStatus.FAILED, failedTx.getStatus());
        assertNull(failedTx.getResultingBalance(), "Failed transactions must show null balance per requirements");
    }

    @Test
    @DisplayName("Integration Test: Verify Account Numbers increment sequentially across users")
    void testSequentialAccountIncrements() {
        BankingService service2 = new BankingService();
        
        String firstAcc = service.openAccount("Bob", 10.00, null);
        String secondAcc = service2.openAccount("Charlie", 20.00, null);

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