import domain.AccountNumberGenerator;
import domain.BankAccount;
import domain.SecurityQuestion;
import domain.SecurityUtil;
import domain.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import repository.BankAccountDAO;
import service.BankingService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    private final InputStream originalIn = System.in;
    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream testOut;

    @BeforeEach
    void setUpOutputBuffer() {
        AccountNumberGenerator.reset();
        testOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(testOut));
    }

    @AfterEach
    void restoreSystemStreams() {
        System.setIn(originalIn);
        System.setOut(originalOut);
    }

    private void provideMockInput(String data) {
        ByteArrayInputStream testIn = new ByteArrayInputStream(data.getBytes());
        System.setIn(testIn);
    }

    private String getConsoleOutput() {
        return testOut.toString();
    }

    private void runApp() {
        runApp(new BankingService(new InMemoryBankAccountDAO()));
    }

    private void runApp(BankingService bankingService) {
        new App(bankingService).runAppLoop();
    }

    private BankingService createAuthenticatedService(InMemoryBankAccountDAO accountDAO, Double dailyLimit) {
        BankingService bankingService = new BankingService(accountDAO);
        bankingService.openAccount(
                "Profile User",
                100.00,
                dailyLimit,
                "1234",
                SecurityQuestion.FIRST_PET.getText(),
                "Milo"
        );
        return bankingService;
    }

    @Test
    @DisplayName("UI Test: Unauthenticated menu handles exit cleanly")
    void testUnauthenticatedMenuExit() {
        // Option 3 is exit on unauthenticated gateway menu shell
        provideMockInput("3\n");

        runApp();

        String output = getConsoleOutput();
        assertTrue(output.contains("=== WELCOME TO CORE BANKING SYSTEM ==="));
        assertTrue(output.contains("Thank you for banking with us. Goodbye!"));
    }

    @Test
    @DisplayName("UI Test: Unauthenticated menu rejects invalid out-of-bounds choices")
    void testUnauthenticatedMenuInvalidChoice() {
        // Sequence: 99 (invalid choice), then 3 (exit system to stop loop)
        provideMockInput("99\n3\n");

        runApp();

        String output = getConsoleOutput();
        assertTrue(output.contains("Invalid choice! Please select an option between 1 and 3."));
    }

    @Test
    @DisplayName("UI Test: Gracefully traps invalid numeric inputs on account configuration formats")
    void testOpenAccountInvalidNumericFormat() {
        // Sequence: 
        // 1 (Open Account)
        // TestingUser (Name)
        // NOT_A_NUMBER (Trash value to force exception string conversion errors)
        // 3 (Exit System)
        provideMockInput("1\nTestingUser\nNOT_A_NUMBER\n3\n");

        runApp();

        String output = getConsoleOutput();
        assertTrue(output.contains("ERROR: Invalid numeric input format entered."));
    }

    @Test
    @DisplayName("UI Test: Empty spacing variations are discarded without corrupting route alignment")
    void testBlankSpammingMitigation() {
        // Sequence:
        // [Newline] (Empty space entry)
        // [Newline] (Empty space entry)
        // 3 (Exit System)
        provideMockInput("\n\n3\n");

        runApp();

        String output = getConsoleOutput();
        assertTrue(output.contains("Thank you for banking with us. Goodbye!"));
        // Confirm it didn't trigger invalid choice warnings for the empty returns
        assertFalse(output.contains("Invalid choice"));
    }

    @Test
    @DisplayName("UI Test: Incorrect PIN is rejected without creating a session")
    void testIncorrectPinRejected() {
        provideMockInput("""
                1
                Secure User
                100

                1234
                1
                Milo
                5
                2
                1001
                9999
                3
                """);

        runApp();

        String output = getConsoleOutput();
        assertTrue(output.contains("Invalid account number or PIN entered."));
        assertFalse(output.contains("Login successful! Welcome back, Secure User!"));
    }

    @Test
    @DisplayName("UI Test: Correct PIN grants access to the authenticated session")
    void testCorrectPinGrantsSessionAccess() {
        provideMockInput("""
                1
                Secure User
                100

                1234
                1
                Milo
                5
                2
                1001
                1234
                5
                3
                """);

        runApp();

        String output = getConsoleOutput();
        assertTrue(output.contains("Login successful! Welcome back, Secure User!"));
        assertTrue(output.contains("SESSION: Secure User (1001)"));
    }

    @Test
    @DisplayName("UI Test: Account creation uses the predefined security question list")
    void testAccountCreationWithPredefinedSecurityQuestion() {
        InMemoryBankAccountDAO accountDAO = new InMemoryBankAccountDAO();
        BankingService bankingService = new BankingService(accountDAO);
        provideMockInput("""
                1
                Question User
                100

                1234
                4
                Dune
                5
                3
                """);

        runApp(bankingService);

        BankAccount account = accountDAO.findAccountByNumber("1001");
        assertNotNull(account);
        assertEquals(SecurityQuestion.FAVORITE_BOOK.getText(), account.getSecurityQuestion());
        assertEquals(
                SecurityUtil.hashSecurityAnswer("dune", account.getPinSalt()),
                account.getSecurityAnswerHash()
        );

        String output = getConsoleOutput();
        for (SecurityQuestion question : SecurityQuestion.values()) {
            assertTrue(output.contains(question.getText()));
        }
    }

    @Test
    @DisplayName("UI Test: Legacy account completes security enrollment and logs in")
    void testLegacyAccountSecurityEnrollment() {
        InMemoryBankAccountDAO accountDAO = new InMemoryBankAccountDAO();
        accountDAO.saveAccount(new BankAccount("1001", "Legacy User", 100.00, null));
        BankingService bankingService = new BankingService(accountDAO);
        provideMockInput("""
                2
                1001
                Legacy User
                2468
                2
                  NAIROBI
                5
                3
                """);

        runApp(bankingService);

        BankAccount account = accountDAO.findAccountByNumber("1001");
        assertNotNull(account.getPinHash());
        assertNotNull(account.getPinSalt());
        assertEquals(SecurityQuestion.BIRTH_CITY.getText(), account.getSecurityQuestion());
        assertEquals(
                SecurityUtil.hashSecurityAnswer("nairobi", account.getPinSalt()),
                account.getSecurityAnswerHash()
        );

        String output = getConsoleOutput();
        assertTrue(output.contains("Account security enrollment completed successfully!"));
        assertTrue(output.contains("Login successful! Welcome back, Legacy User!"));
    }

    @Test
    @DisplayName("UI Test: Forgotten PIN reset verifies the answer and grants access")
    void testForgottenPinResetFlow() {
        InMemoryBankAccountDAO accountDAO = new InMemoryBankAccountDAO();
        BankingService bankingService = new BankingService(accountDAO);
        bankingService.openAccount(
                "Reset User",
                100.00,
                null,
                "1234",
                SecurityQuestion.FAVORITE_BOOK.getText(),
                "Dune"
        );
        bankingService.logout();
        provideMockInput("""
                2
                1001
                RESET
                  DUNE
                5678
                5
                2
                1001
                5678
                5
                3
                """);

        runApp(bankingService);

        BankAccount account = accountDAO.findAccountByNumber("1001");
        assertEquals(SecurityUtil.hashPin("5678", account.getPinSalt()), account.getPinHash());
        assertEquals(
                SecurityUtil.hashSecurityAnswer("dune", account.getPinSalt()),
                account.getSecurityAnswerHash()
        );

        String output = getConsoleOutput();
        assertTrue(output.contains("Security question: " + SecurityQuestion.FAVORITE_BOOK.getText()));
        assertTrue(output.contains("Security answer verified successfully!"));
        assertTrue(output.contains("Login successful! Welcome back, Reset User!"));
    }

    @Test
    @DisplayName("UI Test: Profile settings update the account display name")
    void testProfileDisplayNameUpdate() {
        InMemoryBankAccountDAO accountDAO = new InMemoryBankAccountDAO();
        BankingService bankingService = createAuthenticatedService(accountDAO, null);
        provideMockInput("""
                6
                1
                Updated Profile User
                4
                5
                3
                """);

        runApp(bankingService);

        assertEquals(
                "Updated Profile User",
                accountDAO.findAccountByNumber("1001").getOwnerName()
        );
        assertTrue(getConsoleOutput().contains("Display name updated successfully!"));
    }

    @Test
    @DisplayName("UI Test: Profile settings report an unchanged display name")
    void testProfileDisplayNameUnchanged() {
        InMemoryBankAccountDAO accountDAO = new InMemoryBankAccountDAO();
        BankingService bankingService = createAuthenticatedService(accountDAO, null);
        provideMockInput("""
                6
                1
                Profile User
                4
                5
                3
                """);

        runApp(bankingService);

        assertEquals(0, accountDAO.getProfileUpdateCount());
        assertTrue(
                getConsoleOutput().contains(
                        "Display name is already set to 'Profile User'. No changes were made."
                )
        );
    }

    @Test
    @DisplayName("UI Test: Profile settings update the daily withdrawal limit")
    void testProfileDailyLimitUpdate() {
        InMemoryBankAccountDAO accountDAO = new InMemoryBankAccountDAO();
        BankingService bankingService = createAuthenticatedService(accountDAO, null);
        provideMockInput("""
                6
                2
                75.50
                4
                5
                3
                """);

        runApp(bankingService);

        assertEquals(
                75.50,
                accountDAO.findAccountByNumber("1001").getDailyWithdrawalLimit()
        );
        assertTrue(getConsoleOutput().contains("Daily withdrawal limit updated to $75.50!"));
    }

    @Test
    @DisplayName("UI Test: Profile settings report an unchanged daily withdrawal limit")
    void testProfileDailyLimitUnchanged() {
        InMemoryBankAccountDAO accountDAO = new InMemoryBankAccountDAO();
        BankingService bankingService = createAuthenticatedService(accountDAO, 400.00);
        provideMockInput("""
                6
                2
                400
                4
                5
                3
                """);

        runApp(bankingService);

        assertEquals(0, accountDAO.getProfileUpdateCount());
        assertTrue(
                getConsoleOutput().contains(
                        "Daily withdrawal limit of $400.00 remains unchanged."
                )
        );
    }

    @Test
    @DisplayName("UI Test: Profile settings remove the daily withdrawal limit")
    void testProfileDailyLimitRemoval() {
        InMemoryBankAccountDAO accountDAO = new InMemoryBankAccountDAO();
        BankingService bankingService = createAuthenticatedService(accountDAO, 50.00);
        provideMockInput("""
                6
                2

                4
                5
                3
                """);

        runApp(bankingService);

        assertNull(accountDAO.findAccountByNumber("1001").getDailyWithdrawalLimit());
        assertTrue(getConsoleOutput().contains("Daily withdrawal limit removed successfully!"));
    }

    @Test
    @DisplayName("UI Test: Profile settings change the security PIN")
    void testProfilePinChange() {
        InMemoryBankAccountDAO accountDAO = new InMemoryBankAccountDAO();
        BankingService bankingService = createAuthenticatedService(accountDAO, null);
        provideMockInput("""
                6
                3
                1234
                5678
                4
                5
                2
                1001
                5678
                5
                3
                """);

        runApp(bankingService);

        BankAccount account = accountDAO.findAccountByNumber("1001");
        assertEquals(SecurityUtil.hashPin("5678", account.getPinSalt()), account.getPinHash());
        assertDoesNotThrow(() -> bankingService.verifySecurityAnswer("1001", "Milo"));
        assertTrue(getConsoleOutput().contains("Security PIN changed successfully!"));
        assertTrue(getConsoleOutput().contains("Login successful! Welcome back, Profile User!"));
    }

    @Test
    @DisplayName("UI Test: PIN change rejects a new PIN identical to the current PIN")
    void testProfilePinChangeRejectsIdenticalPin() {
        InMemoryBankAccountDAO accountDAO = new InMemoryBankAccountDAO();
        BankingService bankingService = createAuthenticatedService(accountDAO, null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> bankingService.changePin("1234", "1234")
        );

        assertEquals(
                "New PIN cannot be identical to the current PIN.",
                error.getMessage()
        );
    }

    @Test
    @DisplayName("UI Test: PIN change rejects an invalid new PIN format")
    void testProfilePinChangeRejectsInvalidFormat() {
        InMemoryBankAccountDAO accountDAO = new InMemoryBankAccountDAO();
        BankingService bankingService = createAuthenticatedService(accountDAO, null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> bankingService.changePin("1234", "12AB")
        );

        assertEquals(
                "New PIN must be exactly 4 numeric digits.",
                error.getMessage()
        );
    }

    private static final class InMemoryBankAccountDAO implements BankAccountDAO {
        private final Map<String, BankAccount> accounts = new HashMap<>();
        private int profileUpdateCount;

        int getProfileUpdateCount() {
            return profileUpdateCount;
        }

        @Override
        public void saveAccount(BankAccount account) {
            accounts.put(account.getAccountNumber(), account);
        }

        @Override
        public BankAccount findAccountByNumber(String accountNumber) {
            return accounts.get(accountNumber);
        }

        @Override
        public void updateAccountBalance(String accountNumber, double newBalance) {
            if (!accounts.containsKey(accountNumber)) {
                throw new IllegalArgumentException("Account number not found.");
            }
        }

        @Override
        public void updateAccountProfile(String accountNumber, String newName, Double newLimit) {
            BankAccount account = accounts.get(accountNumber);
            if (account == null) {
                throw new IllegalArgumentException("Account number not found.");
            }
            account.setOwnerName(newName);
            account.setDailyWithdrawalLimit(newLimit);
            profileUpdateCount++;
        }

        @Override
        public void updateAccountSecurity(String accountNumber, String pinHash, String pinSalt,
                                          String securityQuestion, String securityAnswerHash) {
            BankAccount account = accounts.get(accountNumber);
            if (account == null) {
                throw new IllegalArgumentException("Account number not found.");
            }
            account.setPinHash(pinHash);
            account.setPinSalt(pinSalt);
            account.setSecurityQuestion(securityQuestion);
            account.setSecurityAnswerHash(securityAnswerHash);
        }

        @Override
        public void logTransaction(String accountNumber, Transaction transaction) {
            if (!accounts.containsKey(accountNumber)) {
                throw new IllegalArgumentException("Account number not found.");
            }
        }

        @Override
        public List<Transaction> getTransactionHistory(String accountNumber) {
            BankAccount account = accounts.get(accountNumber);
            return account == null ? List.of() : account.getTransactionHistory();
        }

        @Override
        public String getMaxAccountNumber() {
            return accounts.keySet().stream().max(String::compareTo).orElse(null);
        }
    }
}
