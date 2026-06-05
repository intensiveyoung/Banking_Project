package domain;

import java.util.concurrent.atomic.AtomicInteger;

public class AccountNumberGenerator {
    // Starts at 1001
    private static final AtomicInteger counter = new AtomicInteger(1000);
    public static String getNextAccountNumber() {
        return String.valueOf(counter.incrementAndGet());
    }

    public static void reset() {
        counter.set(1000);  // FOR TESTING PURPOSES ONLY
    }
}