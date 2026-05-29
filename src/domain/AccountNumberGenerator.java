package domain;

import java.util.concurrent.atomic.AtomicInteger;

public class AccountNumberGenerator {
    // Starts at 1000 so the first incremented account is 1001
    private static final AtomicInteger counter = new AtomicInteger(1000);

    /**
     * Increments the thread-safe counter and returns the next available account number.
     * @return A unique sequential String identifier.
     */
    public static String getNextAccountNumber() {
        return String.valueOf(counter.incrementAndGet());
    }

    public static void reset() {
        counter.set(1000);  // FOR TESTING PURPOSES ONLY
    }
}