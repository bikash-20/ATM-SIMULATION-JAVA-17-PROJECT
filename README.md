# ATM Simulation System
<img width="481" height="555" alt="image" src="https://github.com/user-attachments/assets/4ea0860f-4ca5-4421-b2f3-b5d05b392f58" />

A full-stack banking simulation built with Spring Boot, demonstrating core
Object-Oriented Programming principles through a realistic ATM workflow —
account management, PIN-secured authentication, deposits, withdrawals,
transaction history, and PDF statement generation.

**Course:** [Course Code:  CSE 222  — Object-Oriented Programming]
**Author:** Bikash Talukder
**Submission Date:** [12-07-26]

---

## 1. Project Overview

This project simulates the core functionality of a real-world Automated
Teller Machine (ATM). It was built to demonstrate practical application of
Object-Oriented Programming concepts — encapsulation, abstraction, and
layered architecture — within a working, database-backed web application
rather than a simple console program.

The system supports the full lifecycle of a bank account: creation,
authentication, transactions, and closure, with every action logged and
retrievable as a formatted statement.

---

## 2. Features

| Feature | Description |
|---|---|
| Account Creation | Register a new account with name, account number, PIN, and optional initial deposit |
| PIN Authentication | 3-attempt limit before the account is locked |
| Balance Inquiry | View current balance in real time |
| Deposit | Add funds with input validation |
| Withdrawal | Enforced per-transaction and daily withdrawal limits, with simulated cash denomination breakdown |
| Transaction History | Chronological log of all activity, visualized with a Chart.js graph |
| PIN Change | Update PIN after verifying the current one |
| Account Closure | Deactivate an account with explicit confirmation |
| PDF Statement | Download a formatted transaction statement (via iText) |

---

## 3. Tech Stack

**Backend**
- Java 17
- Spring Boot 3.2.5
- Spring Data JPA
- H2 Database (in-memory)
- iText PDF 5

**Frontend**
- Thymeleaf (server-side templating)
- HTML5 / CSS3
- Vanilla JavaScript
- Chart.js

**Tooling**
- Maven (with wrapper — no local Maven installation required)
- Git / GitHub
- Render (deployment)

---

## 4. Object-Oriented Design

This project was structured to make OOP principles explicit and easy to
explain:

- **Encapsulation** — `Account` and `Transaction` expose no public fields;
  all state is accessed through getters/setters, and business rules (e.g.,
  balance can never go negative) are enforced inside `AtmService`, not by
  the caller.
- **Abstraction** — The controller layer has no knowledge of how data is
  persisted or validated; it only calls methods like `atmService.deposit(...)`
  and trusts the result.
- **Separation of Concerns (layered architecture)** — The application follows
  a standard three-layer design:
Controller  →  Service  →  Repository  →  Database
(HTTP/UI)      (business      (data access)
logic)

  - `AtmController` — handles HTTP requests and page routing only.
  - `AtmService` — owns all business rules (PIN lockout, withdrawal limits,
    denomination breakdown).
  - `AccountRepository` / `TransactionRepository` — Spring Data JPA
    interfaces that handle persistence with zero boilerplate SQL.

- **Composition** — An `Account` *has a* list of `Transaction` objects
  (`@OneToMany`), modeling a real one-to-many banking relationship rather
  than using inheritance where it doesn't belong.

---

## 5. Project Structure

```
atm-simulation/
├── pom.xml
├── mvnw / mvnw.cmd
├── render.yaml
├── src/
│   ├── main/
│   │   ├── java/com/example/atmsimulation/
│   │   │   ├── AtmSimulationApplication.java   # Entry point
│   │   │   ├── model/
│   │   │   │   ├── Account.java                # JPA entity
│   │   │   │   └── Transaction.java            # JPA entity
│   │   │   ├── repository/
│   │   │   │   ├── AccountRepository.java
│   │   │   │   └── TransactionRepository.java
│   │   │   ├── service/
│   │   │   │   └── AtmService.java             # Business logic
│   │   │   └── controller/
│   │   │       └── AtmController.java          # Routes / pages
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── static/css/style.css
│   │       └── templates/atm/                  # Thymeleaf views
│   └── test/
└── README.md
```

---

## 6. Getting Started

### Prerequisites
- Java 17 or later (`java -version` to confirm)
- Git (optional, for version control)

### Running Locally

```bash
git clone https://github.com/<your-username>/<your-repo>.git
cd atm-simulation
./mvnw spring-boot:run        # macOS/Linux
mvnw.cmd spring-boot:run      # Windows
```

Then open:
http://localhost:8080

The first run downloads dependencies and may take a minute or two.

### First-Time Use
1. Select **Create New Account** and register a test account.
2. Return to the start screen, enter the account number, and authenticate
   with the PIN you set.
3. Explore the menu: balance inquiry, deposits, withdrawals, transaction
   history, PIN change, and account closure.

> **Note:** H2 runs in-memory, so all data resets when the application
> stops. This is intentional for a simulation environment. To inspect the
> live database while the app is running, visit
> `http://localhost:8080/h2-console`
> (JDBC URL: `jdbc:h2:mem:atmdb`, username: `sa`, password: blank).

---

## 7. Deployment

The project includes a `render.yaml` for one-click deployment to
[Render](https://render.com):

1. Push the repository to GitHub.
2. On Render, select **New → Blueprint** and connect the repository.
3. Render will automatically use:
   - Build command: `./mvnw clean package -DskipTests`
   - Start command: `java -jar target/atm-simulation-0.0.1-SNAPSHOT.jar`
4. The application reads its port from the `PORT` environment variable
   (`server.port=${PORT:8080}`), which Render sets automatically — no
   manual configuration required.

---

## 8. Known Limitations

- PIN lockout state is stored in-memory (`Map`/`Set`) rather than persisted,
  so it resets on application restart. Acceptable for a simulation; a
  production system would persist this in the database.
- No password hashing is applied to PINs, since this is an academic
  simulation rather than a security-hardened system.
- Denomination breakdown assumes whole-dollar withdrawal amounts.

---

## 9. Future Improvements

- Persist PIN lockout and session state in the database.
- Add role-based access for bank staff/admin views.
- Hash and salt PINs before storage.
- Add automated unit tests for `AtmService` business rules.

---

## 10. License

This project was developed for academic purposes as part of a university
coursework submission.
