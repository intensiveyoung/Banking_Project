package repository;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class TestDatabasePurgeUtility {
    private static final Set<String> LEGACY_TEST_OWNER_NAMES = Set.of(
            "Test Owner",
            "Rainbow",
            "John Doe",
            "Alice",
            "Bob",
            "Charlie",
            "Original Name",
            "Updated Name",
            "History User"
    );

    private TestDatabasePurgeUtility() {}

    public static void main(String[] args) {
        Collection<String> ownerNames = args.length == 0
                ? LEGACY_TEST_OWNER_NAMES
                : new LinkedHashSet<>(Arrays.asList(args));

        PostgresBankAccountDAO accountDAO = new PostgresBankAccountDAO();
        int deletedAccounts = accountDAO.purgeAccountsByOwnerNames(ownerNames);
        System.out.println("Purged " + deletedAccounts + " legacy test account records.");
    }
}
