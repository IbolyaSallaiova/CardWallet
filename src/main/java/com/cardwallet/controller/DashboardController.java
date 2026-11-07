package com.cardwallet.controller;

import com.cardwallet.db.Database;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
public class DashboardController {

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) throws SQLException, JsonProcessingException {
        String role = (String) session.getAttribute("role");
        model.addAttribute("username", session.getAttribute("username"));
        model.addAttribute("role", role);
        model.addAttribute("mode", session.getAttribute("mode"));

        if ("admin".equals(role)) {
            model.addAttribute("users", loadAllUsers());
            model.addAttribute("cards", List.of());
            model.addAttribute("cardsJson", "[]");
        } else {
            List<Map<String, Object>> cards = loadCardsForUser((Integer) session.getAttribute("userId"));
            model.addAttribute("cards", cards);
            model.addAttribute("cardsJson", new ObjectMapper().writeValueAsString(cards));
        }

        return "dashboard";
    }

    private List<Map<String, Object>> loadAllUsers() throws SQLException {
        List<Map<String, Object>> users = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, username, role FROM users ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> user = new LinkedHashMap<>();
                user.put("id", rs.getInt("id"));
                user.put("username", rs.getString("username"));
                user.put("role", rs.getString("role"));
                users.add(user);
            }
        }
        return users;
    }

    private List<Map<String, Object>> loadCardsForUser(int userId) throws SQLException {
        List<Map<String, Object>> cards = new ArrayList<>();

        try (Connection conn = Database.getConnection()) {
            String cardSql = "SELECT id, nickname, card_number, expiry, status, balance_cents " +
                    "FROM user_cards WHERE user_id = ? ORDER BY id";
            try (PreparedStatement ps = conn.prepareStatement(cardSql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int cardId = rs.getInt("id");
                        String cardNumber = rs.getString("card_number");
                        String last4 = cardNumber.substring(cardNumber.length() - 4);

                        Map<String, Object> card = new LinkedHashMap<>();
                        card.put("id", cardId);
                        card.put("nickname", rs.getString("nickname"));
                        card.put("maskedNumber", "•••• •••• •••• " + last4);
                        card.put("fullNumber", cardNumber);
                        card.put("expiry", rs.getString("expiry"));
                        card.put("status", rs.getString("status"));
                        card.put("balanceFormatted", formatMoney(rs.getInt("balance_cents"), false));
                        card.put("payments", loadPaymentsForCard(conn, cardId));
                        cards.add(card);
                    }
                }
            }
        }

        return cards;
    }

    private List<Map<String, Object>> loadPaymentsForCard(Connection conn, int cardId) throws SQLException {
        List<Map<String, Object>> payments = new ArrayList<>();

        String sql = "SELECT occurred_at, merchant, category, amount_cents " +
                "FROM payments WHERE user_card_id = ? ORDER BY occurred_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cardId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> payment = new LinkedHashMap<>();
                    payment.put("date", rs.getString("occurred_at"));
                    payment.put("merchant", rs.getString("merchant"));
                    payment.put("category", rs.getString("category"));
                    payment.put("amountFormatted", formatMoney(rs.getInt("amount_cents"), true));
                    payments.add(payment);
                }
            }
        }

        return payments;
    }

    private static String formatMoney(int cents, boolean withSign) {
        double value = Math.abs(cents) / 100.0;
        String formatted = "$" + MONEY.format(value);
        if (withSign) {
            return (cents >= 0 ? "+" : "-") + formatted;
        }
        return cents < 0 ? "-" + formatted : formatted;
    }

    @PostMapping("/cards")
    public String addCard(@RequestParam String nickname,
                           @RequestParam String cardNumber,
                           @RequestParam String expiry,
                           @RequestParam(defaultValue = "0") String balance,
                           HttpSession session) throws SQLException {
        String role = (String) session.getAttribute("role");
        if ("admin".equals(role)) {
            return "redirect:/dashboard";
        }
        int userId = (Integer) session.getAttribute("userId");
        int balanceCents = parseDollarsToCents(balance);

        String sql = "INSERT INTO user_cards (user_id, nickname, card_number, expiry, status, balance_cents) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, nickname);
            ps.setString(3, cardNumber);
            ps.setString(4, expiry);
            ps.setInt(5, balanceCents);
            ps.executeUpdate();
        }

        return "redirect:/dashboard";
    }

    private static int parseDollarsToCents(String balance) {
        try {
            return (int) Math.round(Double.parseDouble(balance.trim()) * 100);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @PostMapping("/cards/{id}/delete")
    public String deleteCard(@PathVariable int id, HttpSession session) throws SQLException {
        String role = (String) session.getAttribute("role");
        if ("admin".equals(role)) {
            return "redirect:/dashboard";
        }
        int userId = (Integer) session.getAttribute("userId");

        try (Connection conn = Database.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM payments WHERE user_card_id IN " +
                            "(SELECT id FROM user_cards WHERE id = ? AND user_id = ?)")) {
                ps.setInt(1, id);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM user_cards WHERE id = ? AND user_id = ?")) {
                ps.setInt(1, id);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
        }

        return "redirect:/dashboard";
    }

    @PostMapping("/search")
    @ResponseBody
    public Map<String, Object> search(@RequestBody(required = false) Map<String, String> body, HttpSession session) {
        String term = body != null && body.get("term") != null ? body.get("term") : "";
        Object modeAttr = session.getAttribute("mode");
        String mode = modeAttr != null ? modeAttr.toString() : "vulnerable";
        String role = (String) session.getAttribute("role");

        List<List<String>> results = new ArrayList<>();

        if ("admin".equals(role)) {
            return Map.of("results", results, "mode", mode);
        }

        int userId = (Integer) session.getAttribute("userId");

        try (Connection conn = Database.getConnection()) {
            if ("vulnerable".equals(mode)) {
                String sql = "SELECT nickname, card_number, status, balance_cents FROM user_cards " +
                        "WHERE user_id = " + userId + " AND card_number LIKE '%" + term + "%'";
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    collectRows(rs, results);
                } catch (SQLException e) {
                }
            } else {
                String sql = "SELECT nickname, card_number, status, balance_cents FROM user_cards " +
                        "WHERE user_id = ? AND card_number LIKE ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, userId);
                    ps.setString(2, "%" + term + "%");
                    try (ResultSet rs = ps.executeQuery()) {
                        collectRows(rs, results);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return Map.of("results", results, "mode", mode);
    }

    private static void collectRows(ResultSet rs, List<List<String>> results) throws SQLException {
        while (rs.next()) {
            results.add(List.of(
                    rs.getString("nickname"),
                    rs.getString("card_number"),
                    rs.getString("status"),
                    formatMoney(rs.getInt("balance_cents"), false)));
        }
    }
}
