package com.ridepool.web;

import com.ridepool.service.PasswordResetService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/** Forgot password: 1) email or phone  2) enter OTP  3) set new password. */
@Controller
public class PasswordResetController {

    private static final String S_IDENTIFIER = "fp.identifier";
    private static final String S_VERIFIED_USER = "fp.verifiedUserId";
    private static final String S_VERIFIED_AT = "fp.verifiedAt";
    private static final long VERIFIED_VALID_MS = 10 * 60 * 1000L;

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    // ---- Step 1: email or phone ----

    @GetMapping("/forgot-password")
    public String forgotPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgot(@RequestParam String identifier, HttpSession session, Model model) {
        String id = identifier.trim();
        boolean looksLikeEmail = id.contains("@");
        boolean looksLikePhone = id.replaceAll("\\D", "").length() >= 10;
        if (!looksLikeEmail && !looksLikePhone) {
            model.addAttribute("error", "Enter your registered email or a 10-digit phone number.");
            model.addAttribute("identifier", identifier);
            return "forgot-password";
        }
        service.requestOtp(id);
        session.setAttribute(S_IDENTIFIER, id);
        session.removeAttribute(S_VERIFIED_USER);
        return "redirect:/verify-otp";
    }

    // ---- Step 2: OTP ----

    @GetMapping("/verify-otp")
    public String verifyPage(HttpSession session, Model model) {
        String id = (String) session.getAttribute(S_IDENTIFIER);
        if (id == null) {
            return "redirect:/forgot-password";
        }
        model.addAttribute("viaEmail", id.contains("@"));
        return "verify-otp";
    }

    @PostMapping("/verify-otp")
    public String verify(@RequestParam String otp, HttpSession session, Model model) {
        String id = (String) session.getAttribute(S_IDENTIFIER);
        if (id == null) {
            return "redirect:/forgot-password";
        }
        Optional<Long> userId = service.verifyOtp(id, otp);
        if (userId.isEmpty()) {
            model.addAttribute("viaEmail", id.contains("@"));
            model.addAttribute("error", "That code is wrong or has expired.");
            return "verify-otp";
        }
        session.setAttribute(S_VERIFIED_USER, userId.get());
        session.setAttribute(S_VERIFIED_AT, System.currentTimeMillis());
        return "redirect:/reset-password";
    }

    // ---- Step 3: new password ----

    @GetMapping("/reset-password")
    public String resetPage(HttpSession session) {
        return verifiedUserId(session) == null ? "redirect:/forgot-password" : "reset-password";
    }

    @PostMapping("/reset-password")
    public String reset(@RequestParam String password,
                        @RequestParam String confirm,
                        HttpSession session,
                        Model model) {
        Long userId = verifiedUserId(session);
        if (userId == null) {
            return "redirect:/forgot-password";
        }
        String error = null;
        if (password.length() < 6) {
            error = "Use a password with at least 6 characters.";
        } else if (!password.equals(confirm)) {
            error = "The two passwords don't match.";
        }
        if (error != null) {
            model.addAttribute("error", error);
            return "reset-password";
        }
        service.resetPassword(userId, password);
        session.removeAttribute(S_IDENTIFIER);
        session.removeAttribute(S_VERIFIED_USER);
        session.removeAttribute(S_VERIFIED_AT);
        model.addAttribute("done", true);
        return "reset-password";
    }

    private Long verifiedUserId(HttpSession session) {
        Object id = session.getAttribute(S_VERIFIED_USER);
        Object at = session.getAttribute(S_VERIFIED_AT);
        if (id instanceof Long uid && at instanceof Long ts && System.currentTimeMillis() - ts < VERIFIED_VALID_MS) {
            return uid;
        }
        return null;
    }
}
