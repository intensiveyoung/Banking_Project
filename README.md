# Core Banking System Engine (MVP v1.0.0)

A multi-tiered, robust, and highly predictable terminal-based core banking simulation engine. This application transitions from standard transient volatile JVM memory arrays into a persistent PostgreSQL database engine layout utilizing professional enterprise design patterns.

---

## Architectural Core Patterns

The repository is built following a clean separation of concerns, ensuring business rules remain isolated from external network data protocols:

- **Domain Driven Layers:** Pure entity models (`BankAccount`, `Transaction`) holding critical banking validations, constants, and atomic invariant guardrails.
- **Data Access Object (DAO) Pattern:** A dedicated decoupled abstraction layer interface (`BankAccountDAO`) that hides raw SQL interactions from the core business processing logic.
- **State-Context UI Machine:** A split terminal console loop that restricts visual operation availability depending on whether a customer identity context session has been authenticated or destroyed.
- **Synchronous Input Stream Defense:** Custom input tokenizers that protect the runtime app loop against hardware-buffered keyboard multi-tap or empty stream overflows.

---

## Tech Stack & Requirements

- **Runtime Environment:** Java Development Kit (JDK) 17 or higher
- **Persistence Store:** PostgreSQL Database Engine (listening over local port `5433` or `5432`)
- **Database Driver:** PostgreSQL JDBC Driver (42.x)
- **Testing Engine:** JUnit 5 Jupiter Core Framework

---

## Local Installation & Execution Guide

### 1. Relational Schema Provisioning
Ensure your local PostgreSQL engine service is active. Spin up a new target database instance titled `banking_db`. 

On cold-boot execution, the application automatically provisions the following required table structures within your cluster if they do not already exist:
- `accounts` (Primary validation index tracking names, numbers, balances, and operational limits)
- `transactions` (Historical audit log tracking type streams, stamps, amounts, and completion flags)

### 2. Configuration Adjustments
Open `src/repository/PostgresBankAccountDAO.java` and adjust your connection connection properties to match your local runtime environment instance configurations:

```java
private final String url = "jdbc:postgresql://localhost:5433/banking_db?sslmode=disable";
private final String user = "postgres";
private final String password = "YOUR_DATABASE_SECURE_PASSWORD";
```

### 3. Execution Via JAR Binary
Download the pre-compiled executable binary directly from the GitHub Releases timeline tab block, and execute via terminal shell command:

```bash
java -jar banking_project.jar
```

## Comprehensive Testing Suite

The codebase supports automated verification layers covering business limits, system database integrations, and presentation-layer string streams. Execute the entire matrix runner via your terminal:

```bash
# Run all deterministic units, frozen clock simulations, and stream UI mocks
mvn test
```

* **Unit Tests:** Simulates time-drift and limit exhaust tracking mechanisms utilizing a frozen mock injection of `java.time.Clock`.
* **UI Automation Tests:** Uses stream-redirection tactics (`System.setIn` and `System.setOut`) to pipe programmatic mock interaction commands into the console interface loop to test input exception limits automatically.

---

_Licensed under the MIT License._
