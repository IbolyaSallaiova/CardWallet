package com.cardwallet.controller;

import com.cardwallet.db.Database;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

@Controller
public class AuthController {

    private static final Set<String> VALID_MODES = Set.of("vulnerable", "secure");

    private static String currentMode(HttpSession session) {
        Object mode = session.getAttribute("mode");
        return mode != null ? mode.toString() : "vulnerable";
    }

    private static void startSessionFor(HttpSession session, int id, String username, String role, String mode) {
        session.setAttribute("userId", id);
        session.setAttribute("username", username);
        session.setAttribute("role", role);
        session.setAttribute("mode", mode);
    }

    @GetMapping("/")
    public String index(HttpSession session) {
        if (session.getAttribute("userId") != null) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login(HttpSession session, Model model) {
        model.addAttribute("mode", currentMode(session));
        return "login";
    }

    @PostMapping("/login/vulnerable")
    public String loginVulnerable(@RequestParam String username,
                                   @RequestParam String password,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        String query = "SELECT id, username, role FROM users " +
                "WHERE username = '" + username + "' " +
                "AND password = '" + password + "'";

        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            if (rs.next()) {
                startSessionFor(session, rs.getInt("id"), rs.getString("username"), rs.getString("role"), "vulnerable");
                return "redirect:/dashboard";
            }
        } catch (SQLException e) {
        }

        session.setAttribute("mode", "vulnerable");
        redirectAttributes.addFlashAttribute("error", "Invalid username or password.");
        return "redirect:/login";
    }

    @PostMapping("/login/secure")
    public String loginSecure(@RequestParam String username,
                               @RequestParam String password,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        String sql = "SELECT id, username, role FROM users WHERE username = ? AND password = ?";

        try (Connection conn = Database.getConnection();
             var ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    startSessionFor(session, rs.getInt("id"), rs.getString("username"), rs.getString("role"), "secure");
                    return "redirect:/dashboard";
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        session.setAttribute("mode", "secure");
        redirectAttributes.addFlashAttribute("error", "Invalid username or password.");
        return "redirect:/login";
    }

    @PostMapping("/mode/{modeName}")
    public String switchMode(@PathVariable String modeName,
                              HttpServletRequest request) {
        if (!VALID_MODES.contains(modeName)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND);
        }

        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession newSession = request.getSession(true);
        newSession.setAttribute("mode", modeName);

        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login";
    }
}
