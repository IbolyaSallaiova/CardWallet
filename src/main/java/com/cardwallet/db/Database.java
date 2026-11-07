package com.cardwallet.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    private static final String DEFAULT_DB_PATH = "cardvault.db";

    private Database() {
    }

    public static String getDbPath() {
        String override = System.getProperty("cardvault.db");
        if (override != null) {
            return override;
        }
        String env = System.getenv("CARDVAULT_DB");
        return env != null ? env : DEFAULT_DB_PATH;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + getDbPath());
    }

    public static void initDb(boolean reset) throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            if (reset) {
                stmt.execute("DROP TABLE IF EXISTS payments");
                stmt.execute("DROP TABLE IF EXISTS user_cards");
                stmt.execute("DROP TABLE IF EXISTS users");
            }

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    username TEXT NOT NULL UNIQUE," +
                "    password TEXT NOT NULL," +
                "    role TEXT NOT NULL" +
                ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS user_cards (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    user_id INTEGER NOT NULL," +
                "    nickname TEXT NOT NULL," +
                "    card_number TEXT NOT NULL," +
                "    expiry TEXT NOT NULL," +
                "    status TEXT NOT NULL," +
                "    balance_cents INTEGER NOT NULL" +
                ")"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS payments (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    user_card_id INTEGER NOT NULL," +
                "    occurred_at TEXT NOT NULL," +
                "    merchant TEXT NOT NULL," +
                "    category TEXT NOT NULL," +
                "    amount_cents INTEGER NOT NULL" +
                ")"
            );

            seedUsers(conn, stmt);
            seedUserCardsAndPayments(conn, stmt);
        }
    }

    private static boolean isEmpty(Statement stmt, String table) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1) == 0;
        }
    }

    private static void seedUsers(Connection conn, Statement stmt) throws SQLException {
        if (!isEmpty(stmt, "users")) {
            return;
        }
        String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            insertRow(ps, "admin", "admin123", "admin");
            insertRow(ps, "ibi", "ibi123", "user");
            insertRow(ps, "anna", "anna123", "user");
        }
    }

    private static void insertRow(PreparedStatement ps, String a, String b, String c) throws SQLException {
        ps.setString(1, a);
        ps.setString(2, b);
        ps.setString(3, c);
        ps.executeUpdate();
    }

    private static int userIdFor(Connection conn, String username) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("id");
            }
        }
    }

    private static int insertUserCard(Connection conn, int userId, String nickname, String cardNumber,
                                       String expiry, String status, int balanceCents) throws SQLException {
        String sql = "INSERT INTO user_cards (user_id, nickname, card_number, expiry, status, balance_cents) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, nickname);
            ps.setString(3, cardNumber);
            ps.setString(4, expiry);
            ps.setString(5, status);
            ps.setInt(6, balanceCents);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private static void insertPayment(Connection conn, int userCardId, String occurredAt, String merchant,
                                       String category, int amountCents) throws SQLException {
        String sql = "INSERT INTO payments (user_card_id, occurred_at, merchant, category, amount_cents) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userCardId);
            ps.setString(2, occurredAt);
            ps.setString(3, merchant);
            ps.setString(4, category);
            ps.setInt(5, amountCents);
            ps.executeUpdate();
        }
    }

    private static void seedUserCardsAndPayments(Connection conn, Statement stmt) throws SQLException {
        if (!isEmpty(stmt, "user_cards")) {
            return;
        }

        int ibiId = userIdFor(conn, "ibi");
        int annaId = userIdFor(conn, "anna");

        int ibiEveryday = insertUserCard(conn, ibiId, "Everyday", "5391 2240 7781 4821", "03/28", "ACTIVE", 98450);
        insertPayment(conn, ibiEveryday, "2026-10-03", "Café Luma", "Dining", -1200);
        insertPayment(conn, ibiEveryday, "2026-10-09", "Spotify", "Entertainment", -1299);
        insertPayment(conn, ibiEveryday, "2026-10-15", "Grocery Mart", "Groceries", -8750);
        insertPayment(conn, ibiEveryday, "2026-10-22", "Salary Deposit", "Income", 320000);

        int ibiTravel = insertUserCard(conn, ibiId, "Travel", "4821 7745 3301 8890", "09/28", "ACTIVE", 54200);
        insertPayment(conn, ibiTravel, "2026-09-30", "SBB Travel", "Transport", -11200);
        insertPayment(conn, ibiTravel, "2026-10-08", "Hotel Alpenblick", "Travel", -24500);
        insertPayment(conn, ibiTravel, "2026-10-14", "Airport Lounge", "Travel", -4500);

        int annaStudent = insertUserCard(conn, annaId, "Student", "5500 1123 6689 0021", "05/29", "ACTIVE", 21340);
        insertPayment(conn, annaStudent, "2026-10-05", "Campus Bookstore", "Education", -6200);
        insertPayment(conn, annaStudent, "2026-10-11", "Café Luma", "Dining", -950);
        insertPayment(conn, annaStudent, "2026-10-19", "Allowance", "Income", 40000);
    }
}
