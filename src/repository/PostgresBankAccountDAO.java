package repository;

import domain.*;
import java.sql.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PostgresBankAccountDAO implements BankAccountDAO {
    private final String url = "jdbc:postgresql://localhost:5433/banking_db"; // edit this if different port/db name is used
    private final String user = "postgres";
    private final String password = "user";
    private final Clock clock;

    public PostgresBankAccountDAO() {
        this(Clock.systemDefaultZone());
    }

    public PostgresBankAccountDAO(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock is required.");

        // Automatically set up the relational schema tables if they do not exist on boot
        try (Connection conn = getConnection()) {
            String createAccountsTable = """
                CREATE TABLE IF NOT EXISTS accounts (
                    account_number VARCHAR(10) PRIMARY KEY,
                    owner_name VARCHAR(100) NOT NULL,
                    balance DOUBLE PRECISION NOT NULL,
                    daily_limit DOUBLE PRECISION
                );
                """;
            String createTransactionsTable = """
                CREATE TABLE IF NOT EXISTS transactions (
                    id SERIAL PRIMARY KEY,
                    account_number VARCHAR(10) REFERENCES accounts(account_number),
                    type VARCHAR(20) NOT NULL,
                    amount DOUBLE PRECISION NOT NULL,
                    timestamp TIMESTAMP NOT NULL,
                    resulting_balance DOUBLE PRECISION,
                    status VARCHAR(20) NOT NULL
                );
                """;
            String addPinHashColumn = "ALTER TABLE accounts ADD COLUMN IF NOT EXISTS pin_hash VARCHAR(255)";
            String addPinSaltColumn = "ALTER TABLE accounts ADD COLUMN IF NOT EXISTS pin_salt VARCHAR(255)";
            String addSecurityQuestionColumn = "ALTER TABLE accounts ADD COLUMN IF NOT EXISTS security_question VARCHAR(255)";
            String addSecurityAnswerHashColumn = "ALTER TABLE accounts ADD COLUMN IF NOT EXISTS security_answer_hash VARCHAR(255)";

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createAccountsTable);
                stmt.execute(createTransactionsTable);
                stmt.execute(addPinHashColumn);
                stmt.execute(addPinSaltColumn);
                stmt.execute(addSecurityQuestionColumn);
                stmt.execute(addSecurityAnswerHashColumn);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database initialization failed: " + e.getMessage(), e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public String getMaxAccountNumber() {
        String sql = "SELECT MAX(account_number) FROM accounts";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(1); // Returns highest account number string (e.g., "1001"), or null if table is empty
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error reading max account number boundary", e);
        }
        return null;
    }

    @Override
    public void saveAccount(BankAccount account) {
        String sql = """
                INSERT INTO accounts (
                    account_number,
                    owner_name,
                    balance,
                    daily_limit,
                    pin_hash,
                    pin_salt,
                    security_question,
                    security_answer_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, account.getAccountNumber());
            pstmt.setString(2, account.getOwnerName());
            pstmt.setDouble(3, account.getBalance());

            // Use a clean wrapper validation check
            if (account.getDailyWithdrawalLimit() != null) {
                pstmt.setDouble(4, account.getDailyWithdrawalLimit());
            } else {
                // Explicitly pass a universal SQL NULL type marker
                pstmt.setNull(4, java.sql.Types.NULL);
            }

            setNullableString(pstmt, 5, account.getPinHash());
            setNullableString(pstmt, 6, account.getPinSalt());
            setNullableString(pstmt, 7, account.getSecurityQuestion());
            setNullableString(pstmt, 8, account.getSecurityAnswerHash());
            pstmt.executeUpdate();

            if (!account.getTransactionHistory().isEmpty()) {
                logTransaction(account.getAccountNumber(), account.getTransactionHistory().get(0));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error saving account to DB: " + e.getMessage(), e); // Appended message for clear debugging
        }
    }

    @Override
    public BankAccount findAccountByNumber(String accountNumber) {
        String sql = "SELECT * FROM accounts WHERE account_number = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, accountNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("owner_name");
                    double bal = rs.getDouble("balance");
                    double limitVal = rs.getDouble("daily_limit");
                    Double limit = rs.wasNull() ? null : limitVal;
                    String pinHash = rs.getString("pin_hash");
                    String pinSalt = rs.getString("pin_salt");
                    String securityQuestion = rs.getString("security_question");
                    String securityAnswerHash = rs.getString("security_answer_hash");

                    // Rehydrate the domain object state from database data metrics
                    BankAccount account = BankAccount.rehydrate(accountNumber, name, bal, limit);
                    account.setPinHash(pinHash);
                    account.setPinSalt(pinSalt);
                    account.setSecurityQuestion(securityQuestion);
                    account.setSecurityAnswerHash(securityAnswerHash);

                    // Re-populate transaction list histories securely
                    // Note: We bypass the base constructor's auto-added initial record to mirror the DB accurately
                    return account;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching account from DB", e);
        }
        return null;
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    @Override
    public void updateAccountBalance(String accountNumber, double newBalance) {
        String sql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, newBalance);
            pstmt.setString(2, accountNumber);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating balance configuration", e);
        }
    }

    @Override
    public void transferFunds(String sourceAcc, String targetAcc, double amount) {
        String lockAccountsSql = """
                SELECT account_number, balance FROM accounts
                WHERE account_number IN (?, ?)
                ORDER BY account_number FOR UPDATE
                """;
        String updateBalanceSql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
        String insertTransactionSql = """
                INSERT INTO transactions (account_number, type, amount, timestamp, resulting_balance, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement lockAccounts = conn.prepareStatement(lockAccountsSql);
                 PreparedStatement updateBalance = conn.prepareStatement(updateBalanceSql);
                 PreparedStatement insertTransaction = conn.prepareStatement(insertTransactionSql)) {
                lockAccounts.setString(1, sourceAcc);
                lockAccounts.setString(2, targetAcc);
                double sourceBalance = Double.NaN;
                double targetBalance = Double.NaN;
                try (ResultSet resultSet = lockAccounts.executeQuery()) {
                    while (resultSet.next()) {
                        if (sourceAcc.equals(resultSet.getString("account_number"))) {
                            sourceBalance = resultSet.getDouble("balance");
                        } else {
                            targetBalance = resultSet.getDouble("balance");
                        }
                    }
                }
                if (!Double.isFinite(sourceBalance) || !Double.isFinite(targetBalance)) {
                    throw new SQLException("Both accounts must exist to complete a transfer.");
                }
                if (amount > sourceBalance) {
                    throw new SQLException("Insufficient funds for this transfer.");
                }

                double newSourceBalance = sourceBalance - amount;
                double newTargetBalance = targetBalance + amount;
                updateBalance.setDouble(1, newSourceBalance);
                updateBalance.setString(2, sourceAcc);
                updateBalance.executeUpdate();
                updateBalance.setDouble(1, newTargetBalance);
                updateBalance.setString(2, targetAcc);
                updateBalance.executeUpdate();

                LocalDateTime timestamp = LocalDateTime.now(clock);
                insertTransferTransaction(insertTransaction, sourceAcc, TransactionType.TRANSFER_OUT,
                        amount, timestamp, newSourceBalance);
                insertTransferTransaction(insertTransaction, targetAcc, TransactionType.TRANSFER_IN,
                        amount, timestamp, newTargetBalance);
                conn.commit();
            } catch (SQLException e) {
                rollback(conn, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error transferring funds", e);
        }
    }

    private void insertTransferTransaction(PreparedStatement statement, String accountNumber,
                                           TransactionType type, double amount,
                                           LocalDateTime timestamp, double resultingBalance)
            throws SQLException {
        statement.setString(1, accountNumber);
        statement.setString(2, type.name());
        statement.setDouble(3, amount);
        statement.setTimestamp(4, Timestamp.valueOf(timestamp));
        statement.setDouble(5, resultingBalance);
        statement.setString(6, TransactionStatus.SUCCESS.name());
        statement.executeUpdate();
    }

    @Override
    public void updateAccountProfile(String accountNumber, String newName, Double newLimit) {
        String sql = "UPDATE accounts SET owner_name = ?, daily_limit = ? WHERE account_number = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newName);
            if (newLimit == null) {
                pstmt.setNull(2, Types.DOUBLE);
            } else {
                pstmt.setDouble(2, newLimit);
            }
            pstmt.setString(3, accountNumber);

            if (pstmt.executeUpdate() != 1) {
                throw new IllegalArgumentException("Account number not found.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error updating account profile configuration", e);
        }
    }

    @Override
    public void updateAccountSecurity(String accountNumber, String pinHash, String pinSalt,
                                      String securityQuestion, String securityAnswerHash) {
        String sql = """
                UPDATE accounts
                SET pin_hash = ?, pin_salt = ?, security_question = ?, security_answer_hash = ?
                WHERE account_number = ?
                """;
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, pinHash);
            pstmt.setString(2, pinSalt);
            pstmt.setString(3, securityQuestion);
            pstmt.setString(4, securityAnswerHash);
            pstmt.setString(5, accountNumber);

            if (pstmt.executeUpdate() != 1) {
                throw new IllegalArgumentException("Account number not found.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error updating account security configuration", e);
        }
    }

    @Override
    public void logTransaction(String accountNumber, Transaction transaction) {
        String sql = "INSERT INTO transactions (account_number, type, amount, timestamp, resulting_balance, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, accountNumber);
            pstmt.setString(2, transaction.getType().name());
            pstmt.setDouble(3, transaction.getAmount());
            pstmt.setTimestamp(4, Timestamp.valueOf(transaction.getTimestamp()));
            if (transaction.getResultingBalance() != null) {
                pstmt.setDouble(5, transaction.getResultingBalance());
            } else {
                pstmt.setNull(5, Types.DOUBLE);
            }
            pstmt.setString(6, transaction.getStatus().name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error logging transactional history event", e);
        }
    }

    @Override
    public List<Transaction> getTransactionHistory(String accountNumber) {
        String sql = "SELECT * FROM transactions WHERE account_number = ? ORDER BY timestamp ASC";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, accountNumber);
            return readTransactions(pstmt);
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching transaction stream mapping", e);
        }
    }

    @Override
    public List<Transaction> getTransactionHistoryFiltered(String accountNumber, DurationFilter filter) {
        return getTransactionHistoryFiltered(accountNumber, null, filter, null, null);
    }

    @Override
    public List<Transaction> getTransactionHistoryFiltered(String accountNumber, TransactionType type,
                                                           DurationFilter filter,
                                                           LocalDateTime customStart,
                                                           LocalDateTime customEnd) {
        Objects.requireNonNull(filter, "Duration filter is required.");

        LocalDateTime startDate = customStart;
        LocalDateTime endDate = customEnd;
        if (startDate == null && endDate == null && filter != DurationFilter.ALL_TIME) {
            startDate = LocalDateTime.now(clock).minusDays(filter.getDays());
        } else if (startDate != null && endDate == null) {
            endDate = LocalDateTime.now(clock);
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }

        StringBuilder sql = new StringBuilder(
                "SELECT * FROM transactions WHERE account_number = ?"
        );
        boolean filterByType = type != null && type != TransactionType.ALL;
        if (filterByType) {
            sql.append(type == TransactionType.TRANSFER
                    ? " AND type IN (?, ?, ?)"
                    : " AND type = ?");
        }
        if (startDate != null) {
            sql.append(" AND timestamp >= ?");
        }
        if (endDate != null) {
            sql.append(" AND timestamp <= ?");
        }
        sql.append(" ORDER BY timestamp DESC");

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            int parameterIndex = 1;
            pstmt.setString(parameterIndex++, accountNumber);
            if (filterByType) {
                pstmt.setString(parameterIndex++, type.name());
                if (type == TransactionType.TRANSFER) {
                    pstmt.setString(parameterIndex++, TransactionType.TRANSFER_OUT.name());
                    pstmt.setString(parameterIndex++, TransactionType.TRANSFER_IN.name());
                }
            }
            if (startDate != null) {
                pstmt.setTimestamp(parameterIndex++, Timestamp.valueOf(startDate));
            }
            if (endDate != null) {
                pstmt.setTimestamp(parameterIndex, Timestamp.valueOf(endDate));
            }
            return readTransactions(pstmt);
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching filtered transaction history", e);
        }
    }

    @Override
    public List<Transaction> getTransactionHistoryByDateRange(String accountNumber,
                                                              LocalDateTime startDate,
                                                              LocalDateTime endDate) {
        LocalDateTime resolvedEndDate = endDate == null ? LocalDateTime.now(clock) : endDate;
        if (startDate != null && startDate.isAfter(resolvedEndDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }

        String sql;
        if (startDate == null) {
            sql = """
                    SELECT * FROM transactions
                    WHERE account_number = ? AND timestamp <= ?
                    ORDER BY timestamp DESC
                    """;
        } else {
            sql = """
                    SELECT * FROM transactions
                    WHERE account_number = ? AND timestamp >= ? AND timestamp <= ?
                    ORDER BY timestamp DESC
                    """;
        }

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, accountNumber);
            if (startDate == null) {
                pstmt.setTimestamp(2, Timestamp.valueOf(resolvedEndDate));
            } else {
                pstmt.setTimestamp(2, Timestamp.valueOf(startDate));
                pstmt.setTimestamp(3, Timestamp.valueOf(resolvedEndDate));
            }
            return readTransactions(pstmt);
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching transaction history by date range", e);
        }
    }

    @Override
    public List<Transaction> getRecentTransactions(String accountNumber, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "Mini-statement transaction count must be greater than zero."
            );
        }

        String sql = """
                SELECT * FROM transactions
                WHERE account_number = ?
                ORDER BY timestamp DESC
                LIMIT ?
                """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, accountNumber);
            pstmt.setInt(2, limit);
            return readTransactions(pstmt);
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching recent transactions", e);
        }
    }

    @Override
    public void deleteAccountAndTransactions(String accountNumber) {
        String deleteTransactionsSql = "DELETE FROM transactions WHERE account_number = ?";
        String deleteAccountSql = "DELETE FROM accounts WHERE account_number = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement deleteTransactions = conn.prepareStatement(deleteTransactionsSql);
                 PreparedStatement deleteAccount = conn.prepareStatement(deleteAccountSql)) {
                deleteTransactions.setString(1, accountNumber);
                deleteTransactions.executeUpdate();

                deleteAccount.setString(1, accountNumber);
                deleteAccount.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                rollback(conn, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting account test data", e);
        }
    }

    public int purgeAccountsByOwnerNames(Collection<String> ownerNames) {
        if (ownerNames == null || ownerNames.isEmpty()) {
            return 0;
        }

        String placeholders = String.join(
                ", ",
                Collections.nCopies(ownerNames.size(), "?")
        );
        String deleteTransactionsSql = """
                DELETE FROM transactions
                WHERE account_number IN (
                    SELECT account_number FROM accounts WHERE owner_name IN (%s)
                )
                """.formatted(placeholders);
        String deleteAccountsSql = "DELETE FROM accounts WHERE owner_name IN (%s)"
                .formatted(placeholders);

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement deleteTransactions = conn.prepareStatement(deleteTransactionsSql);
                 PreparedStatement deleteAccounts = conn.prepareStatement(deleteAccountsSql)) {
                bindStrings(deleteTransactions, ownerNames);
                deleteTransactions.executeUpdate();

                bindStrings(deleteAccounts, ownerNames);
                int deletedAccounts = deleteAccounts.executeUpdate();
                conn.commit();
                return deletedAccounts;
            } catch (SQLException e) {
                rollback(conn, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error purging legacy test accounts", e);
        }
    }

    private void bindStrings(PreparedStatement statement, Collection<String> values)
            throws SQLException {
        int index = 1;
        for (String value : values) {
            statement.setString(index++, value);
        }
    }

    private void rollback(Connection connection, SQLException originalError) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            originalError.addSuppressed(rollbackError);
        }
    }

    private List<Transaction> readTransactions(PreparedStatement statement) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                TransactionType type = TransactionType.valueOf(rs.getString("type"));
                double amount = rs.getDouble("amount");
                LocalDateTime time = rs.getTimestamp("timestamp").toLocalDateTime();
                double resultingBalanceValue = rs.getDouble("resulting_balance");
                Double resultingBalance = rs.wasNull() ? null : resultingBalanceValue;
                TransactionStatus status = TransactionStatus.valueOf(rs.getString("status"));

                transactions.add(new Transaction(
                        type,
                        amount,
                        time,
                        resultingBalance,
                        status
                ));
            }
        }
        return transactions;
    }
}
