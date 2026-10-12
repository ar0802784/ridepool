package com.ridepool.web;

import com.ridepool.model.User;
import com.ridepool.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/request";
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        if (session.getAttribute(AuthInterceptor.SESSION_USER_ID) != null) {
            return "redirect:/request";
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        HttpServletRequest request,
                        Model model) {
        Optional<User> found = users.findByEmailIgnoreCase(email.trim());
        if (found.isEmpty() || !encoder.matches(password, found.get().getPasswordHash())) {
            model.addAttribute("error", "That email and password don't match. Check them and try again.");
            model.addAttribute("email", email);
            return "login";
        }
        startSession(request, found.get());
        return "redirect:/request";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String name,
                           @RequestParam String email,
                           @RequestParam String phone,
                           @RequestParam String password,
                           HttpServletRequest request,
                           Model model) {
        String error = null;
        if (name.isBlank() || email.isBlank() || phone.isBlank()) {
            error = "Fill in your name, email and phone number.";
        } else if (!email.contains("@")) {
            error = "Enter a valid email address.";
        } else if (password.length() < 6) {
            error = "Use a password with at least 6 characters.";
        } else if (users.existsByEmailIgnoreCase(email.trim())) {
            error = "That email already has an account. Log in instead.";
        }

        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("name", name);
            model.addAttribute("email", email);
            model.addAttribute("phone", phone);
            return "register";
        }

        User user = new User();
        user.setName(name.trim());
        user.setEmail(email.trim());
        user.setPhone(phone.trim());
        user.setPasswordHash(encoder.encode(password));
        user = users.save(user);

        startSession(request, user);
        return "redirect:/request";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    private void startSession(HttpServletRequest request, User user) {
        // Fresh session id on login prevents session fixation
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(AuthInterceptor.SESSION_USER_ID, user.getId());
        session.setAttribute("userName", user.getName());
    }
}
