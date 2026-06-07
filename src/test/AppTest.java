import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    private final InputStream originalIn = System.in;
    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream testOut;

    @BeforeEach
    void setUpOutputBuffer() {
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

    @Test
    @DisplayName("UI Test: Unauthenticated menu handles exit cleanly")
    void testUnauthenticatedMenuExit() {
        // Option 3 is exit on unauthenticated gateway menu shell
        provideMockInput("3\n");

        App.main(new String[]{});

        String output = getConsoleOutput();
        assertTrue(output.contains("=== WELCOME TO CORE BANKING SYSTEM ==="));
        assertTrue(output.contains("Thank you for banking with us. Goodbye!"));
    }

    @Test
    @DisplayName("UI Test: Unauthenticated menu rejects invalid out-of-bounds choices")
    void testUnauthenticatedMenuInvalidChoice() {
        // Sequence: 99 (invalid choice), then 3 (exit system to stop loop)
        provideMockInput("99\n3\n");

        App.main(new String[]{});

        String output = getConsoleOutput();
        assertTrue(output.contains("❌ Invalid choice! Please select an option between 1 and 3."));
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

        App.main(new String[]{});

        String output = getConsoleOutput();
        assertTrue(output.contains("❌ ERROR: Invalid numeric input format entered."));
    }

    @Test
    @DisplayName("UI Test: Empty spacing variations are discarded without corrupting route alignment")
    void testBlankSpammingMitigation() {
        // Sequence:
        // [Newline] (Empty space entry)
        // [Newline] (Empty space entry)
        // 3 (Exit System)
        provideMockInput("\n\n3\n");

        App.main(new String[]{});

        String output = getConsoleOutput();
        assertTrue(output.contains("Thank you for banking with us. Goodbye!"));
        // Confirm it didn't trigger invalid choice warnings for the empty returns
        assertFalse(output.contains("Invalid choice"));
    }
}