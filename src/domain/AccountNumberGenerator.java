package domain;

import java.util.concurrent.atomic.AtomicInteger;

public class AccountNumberGenerator {
    // Starts at 1001
    private static final AtomicInteger counter = new AtomicInteger(1000);
    public static synchronized String getNextAccountNumber() {
        return String.format("%04d", counter.incrementAndGet());
    }

    // Dynamic database synchronization hook!
    public static void initializeCounter(int highestValue) {
        counter.set(highestValue);
    }

    public static void reset() {
        counter.set(1000);  // FOR TESTING PURPOSES ONLY
    }

}