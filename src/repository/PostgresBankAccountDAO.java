package repository;

import domain.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PostgresBankAccountDAO implements BankAccountDAO {
    private final String url = "jdbc:postgresql://localhost:5433/banking_db"; // edit this if different port/db name is used
    private final String user = "postgres";
    private final String password = "user";

    public PostgresBankAccountDAO() {
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
        List<Transaction> list = new ArrayList<>();
        String sql = "SELECT * FROM transactions WHERE account_number = ? ORDER BY timestamp ASC";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, accountNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    TransactionType type = TransactionType.valueOf(rs.getString("type"));
                    double amount = rs.getDouble("amount");
                    LocalDateTime time = rs.getTimestamp("timestamp").toLocalDateTime();
                    double resBalVal = rs.getDouble("resulting_balance");
                    Double resBal = rs.wasNull() ? null : resBalVal;
                    TransactionStatus status = TransactionStatus.valueOf(rs.getString("status"));

                    list.add(new Transaction(type, amount, time, resBal, status));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching transaction stream mapping", e);
        }
        return list;
    }
}
