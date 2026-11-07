package com.cardwallet.controller;

import com.cardwallet.db.Database;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Controller
public class AdminController {

    @PostMapping("/admin/users")
    public String addUser(@RequestParam String username,
                           @RequestParam String password,
                           @RequestParam String role,
                           HttpSession session) throws SQLException {
        if (!"admin".equals(session.getAttribute("role"))) {
            return "redirect:/dashboard";
        }
        if (!"admin".equals(role) && !"user".equals(role)) {
            return "redirect:/dashboard";
        }

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users (username, password, role) VALUES (?, ?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, role);
            ps.executeUpdate();
        } catch (SQLException e) {
        }

        return "redirect:/dashboard";
    }

    @PostMapping("/admin/users/{id}/delete")
    public String deleteUser(@PathVariable int id, HttpSession session) throws SQLException {
        if (!"admin".equals(session.getAttribute("role"))) {
            return "redirect:/dashboard";
        }
        Integer currentUserId = (Integer) session.getAttribute("userId");
        if (currentUserId != null && currentUserId == id) {
            return "redirect:/dashboard";
        }

        try (Connection conn = Database.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM payments WHERE user_card_id IN " +
                            "(SELECT id FROM user_cards WHERE user_id = ?)")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM user_cards WHERE user_id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        }

        return "redirect:/dashboard";
    }
}
