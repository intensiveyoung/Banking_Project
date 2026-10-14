package util;

import java.io.Console;
import java.util.Arrays;
import java.util.Scanner;

public final class InputUtils {
    private InputUtils() {
    }

    /**
     * Reads a PIN without echo when a console is available. The line-based
     * fallback prints a mask after Enter but cannot control an IDE's own echo.
     * PIN format validation remains with the banking service, including RESET.
     */
    public static String readMaskedPin(Scanner scanner, String prompt) {
        System.out.print(prompt);
        System.out.flush();
        Console console = System.console();
        if (console != null) {
            char[] characters = console.readPassword();
            if (characters == null) {
                return "";
            }
            try {
                return new String(characters).trim();
            } finally {
                Arrays.fill(characters, '\0');
            }
        }

        String input = scanner.hasNextLine() ? scanner.nextLine() : "";
        System.out.println("*".repeat(input.length()));
        return input.trim();
    }
}
