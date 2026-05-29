package domain;

import java.util.concurrent.atomic.AtomicInteger;

public class AccountNumberGenerator {
    // Starts at 10001
    private static final AtomicInteger counter = new AtomicInteger(10000);
    public static String getNextAccountNumber() {
        return String.valueOf(counter.incrementAndGet());
    }
}