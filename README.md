# CardWallet — SQL Injection

## What this demonstrates

SQL Injection vulnerabilities:

1. **Authentication bypass** 
2. **Broken access control via SQL Injection**
3. **Error-based SQL Injection** (database schema disclosure via verbose error messages)

## Tech stack

- **Java 17+** (tested on Java 21)
- **Spring Boot 3.3** (`spring-boot-starter-web` + `spring-boot-starter-thymeleaf`)
- **SQLite** via `org.xerial:sqlite-jdbc`, accessed with raw JDBC
  (`Statement` for the vulnerable paths, `PreparedStatement` for the secure
  paths — no ORM, so the contrast is explicit)
- **Maven**, built via the included Maven Wrapper (`mvnw` / `mvnw.cmd`) —
  you do not need Maven installed system-wide

## Project structure

```
cardwallet/
├── pom.xml
├── mvnw / mvnw.cmd / .mvn/wrapper/maven-wrapper.properties
├── README.md
├── logo.jpg / logo.png
└── src/
    ├── main/
    │   ├── java/com/cardwallet/
    │   │   ├── CardWalletApplication.java   Spring Boot entry point; initializes the DB on startup
    │   │   ├── db/Database.java             Schema + fictional seed data (JDBC) — users, plus
    │   │   │                                 user_cards/payments for the account feature
    │   │   ├── config/AuthInterceptor.java  Redirects unauthenticated requests to /login
    │   │   ├── config/WebConfig.java        Registers the interceptor on /dashboard, /search, /admin/**
    │   │   └── controller/
    │   │       ├── AuthController.java      /, /login, /login/vulnerable, /login/secure, /logout, /mode/{name}
    │   │       ├── DashboardController.java /dashboard (admin user list, or the user's own cards + payments), /search
    │   │       └── AdminController.java     /admin/users (add), /admin/users/{id}/delete — admin-only, always secure
    │   └── resources/
    │       ├── application.properties       server.address=127.0.0.1, port 5000
    │       ├── templates/login.html          Thymeleaf
    │       ├── templates/dashboard.html      Thymeleaf
    │       └── static/{css,js,img}/          Styling, dashboard/search UI script, logo
```

## Setup & running

From `cardwallet/`:

```bash
# Windows
.\mvnw.cmd spring-boot:run

# macOS/Linux
./mvnw spring-boot:run
```

The first run downloads Maven itself (via the wrapper) plus all
dependencies — this needs internet access and can take a few minutes. After
that, everything is cached locally.

Visit **http://127.0.0.1:5000**. The SQLite file `cardvault.db` is created
and seeded automatically on first run (in the project root).

## Demo accounts

| username | password  | role  |
|----------|-----------|-------|
| admin    | admin123  | admin |
| ibi      | ibi123    | user  |
| anna     | anna123   | user  |

## Account cards & payments

`admin` has no cards and cannot see anyone else's — the admin dashboard is a
user-management panel instead (list users, add a user, delete a user;
always `PreparedStatement`, no injection angle). Regular accounts
(`ibi`: Everyday + Travel, `anna`: Student) each own their own set of
fictional cards, stored in the `user_cards` and `payments` tables. On the
dashboard, clicking a card opens a detail panel with its full number,
status, expiry, balance, and recent payment history.

The card **search** panel is where the second SQL Injection vulnerability
lives: it's supposed to show only the logged-in user's own cards (scoped by
`user_id`), but in Vulnerable Mode that scope can be bypassed — see Part 3
below.


## SQL Injection login bypass (Vulnerable Mode)
1. On the login page, select **Vulnerable** mode.
2. Enter:
   - Username: `admin' --`
   - Password: `anything`
3. You are logged in as `admin` without knowing the real password.
4. Explain: the vulnerable query in `AuthController.loginVulnerable` is
   built as

   ```java
   String query = "SELECT id, username, role FROM users " +
           "WHERE username = '" + username + "' " +
           "AND password = '" + password + "'";
   Statement stmt = conn.createStatement();
   ResultSet rs = stmt.executeQuery(query);
   ```

   With the payload, SQLite executes:

   ```sql
   SELECT id, username, role FROM users
   WHERE username = 'admin' --'
   AND password = 'anything'
   ```

   `--` starts an SQL line comment, so everything after it (including the
   password check) is discarded. The query becomes "find the user named
   admin," full stop.

## Broken access control via SQL Injection (Vulnerable Mode)
1. Log in as `ibi` / `ibi123` (Vulnerable Mode). The dashboard shows ibi's
   two cards (Everyday, Travel).
2. In the card search, search `5391` → only ibi's own "Everyday" card is
   returned.
3. Search:

   ```
   ' OR '1'='1' --
   ```

4. Every user's cards appear in the results table.
5. Explain: the vulnerable search query in `DashboardController.search` is

   ```java
   String sql = "SELECT nickname, card_number, status, balance_cents FROM user_cards " +
           "WHERE user_id = " + userId + " AND card_number LIKE '%" + term + "%'";
   Statement stmt = conn.createStatement();
   ResultSet rs = stmt.executeQuery(sql);
   ```

   `userId` is a trusted integer from the session, so concatenating it is
   safe on its own — the bug is the user-controlled `term`. The payload
   closes the `LIKE` string early, adds `OR '1'='1'` (always true), and
   `--` comments out the trailing `%'` so the query still parses. The
   intended filter `user_id = <ibi's id> AND card_number LIKE '%...%'`
   collapses into "true for every row," so the ownership check is
   completely bypassed.

## Error-based SQL Injection (Vulnerable Mode)
1. Log in as `ibi` / `ibi123` (Vulnerable Mode).
2. In the card search, search:

   ```
   ' AND (SELECT 1 FROM nonexistent_table) --
   ```

3. Instead of an empty result, the search panel shows a red error banner
   with the raw SQLite exception message:

   ```
   [SQLITE_ERROR] SQL error or missing database (no such table: nonexistent_table)
   ```

4. Explain: in `DashboardController.search`, the vulnerable branch now
   returns the caught exception's message to the client instead of
   swallowing it:

   ```java
   try (Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql)) {
       collectRows(rs, results);
   } catch (SQLException e) {
       return Map.of("results", results, "mode", mode, "error", e.getMessage());
   }
   ```

   SQLite (like most databases) includes identifiers from the failing
   query in its error text. By injecting different table/column names
   into the same `card_number LIKE '%...%'` entry point used for the
   ownership bypass above, an attacker can map the schema one guess at a
   time — `no such table: X` vs. no error at all confirms whether `X`
   exists — without ever needing the data to come back in a normal
   result row. This is the same injection point as Part 3; it's a second,
   independent way to exploit it.

| | Vulnerable | Secure |
|---|---|---|
| Login | `"...username = '" + username + "'..."` + `Statement.executeQuery(query)` | `PreparedStatement` with `?` placeholders + `setString(1, username)` |
| Search | `"...user_id = " + userId + " AND card_number LIKE '%" + term + "%'"` + `Statement.executeQuery(sql)` | `PreparedStatement` with `?` + `setInt(1, userId)` + `setString(2, "%" + term + "%")` |


## Why plaintext passwords here?

This app stores and compares plaintext passwords in the `users` table so
that the SQL Injection mechanism is easy to see and reason about without
also explaining hashing. **Production systems must never do this.** Real
applications should:

- Hash passwords with a slow, salted algorithm (bcrypt, scrypt, or
  Argon2 — e.g. Spring Security's `BCryptPasswordEncoder`), never store or
  compare raw passwords.
- Use parameterized queries *in addition to* password hashing — the two
  protect against different problems (injection vs. credential leakage).

Parameterized queries alone do not make plaintext password storage safe;
they only prevent SQL Injection.

## Security modes

- **Vulnerable Mode** — login and card search both use raw string
  concatenation with `Statement`, and search exceptions are returned to the
  client. Authentication bypass, the ownership-bypass card leak, and the
  error-based schema disclosure all work.
- **Secure Mode** — login and card search both use `PreparedStatement` with
  bound parameters, and no exception detail is ever returned to the client.
  All three attacks are rejected.

  

