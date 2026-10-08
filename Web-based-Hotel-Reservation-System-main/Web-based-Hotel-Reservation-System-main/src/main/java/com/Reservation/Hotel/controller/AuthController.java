package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.dto.RegistrationForm;
import com.Reservation.Hotel.model.AppUser;
import com.Reservation.Hotel.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import com.Reservation.Hotel.service.ActivityLogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Login page, sign-up and the "forgot password" (security question) flow. */
@Controller
public class AuthController {

    private final UserService userService;

    private final ActivityLogService activityLog;

    public AuthController(UserService userService, ActivityLogService activityLog) {
        this.activityLog = activityLog;
        this.userService = userService;
    }

    private boolean loggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    // ---------------- login ----------------

    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (loggedIn(authentication)) return "redirect:/dashboard";
        return "auth/login";
    }

    // ---------------- register ----------------

    private void prepareRegister(Model model) {
        model.addAttribute("securityQuestions", UserService.SECURITY_QUESTIONS);
    }

    @GetMapping("/register")
    public String showRegister(@RequestParam(value = "role", required = false) String role,
                               Authentication authentication, Model model) {
        if (loggedIn(authentication)) return "redirect:/dashboard";
        RegistrationForm form = new RegistrationForm();
        if (role != null && UserService.SELF_REGISTER_ROLES.contains(role.toUpperCase())) {
            form.setRole(role.toUpperCase());
        }
        model.addAttribute("form", form);
        prepareRegister(model);
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            String problem = userService.checkRegistration(form);
            if (problem != null) {
                model.addAttribute("errorMessage", problem);
            }
        }
        if (bindingResult.hasErrors() || model.containsAttribute("errorMessage")) {
            form.setPassword(null);
            form.setConfirmPassword(null);
            prepareRegister(model);
            return "auth/register";
        }

        AppUser user = userService.register(form);
        activityLog.log(user.getUsername(), "USER_REGISTERED", user.getRoleLabel() + " account " + user.getUsername());
        String next = switch (user.getRole()) {
            case AppUser.ROLE_MANAGER -> " You can now register your hotel - it goes live once an administrator approves it.";
            case AppUser.ROLE_AGENT -> " Complete your agent profile after logging in so an administrator can verify you.";
            default -> "";
        };
        redirectAttributes.addFlashAttribute("successMessage", "Account created! Please log in." + next);
        return "redirect:/login";
    }

    // ---------------- forgot password ----------------

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "auth/forgot-password";
    }

    /** Step 1: the user enters their username and is shown their security question. */
    @PostMapping("/forgot-password")
    public String findAccount(@RequestParam("username") String username, Model model) {
        AppUser user = userService.findByUsername(username).orElse(null);
        if (user == null || user.getSecurityQuestion() == null) {
            model.addAttribute("errorMessage", "We could not find an account with that username.");
            model.addAttribute("username", username);
            return "auth/forgot-password";
        }
        model.addAttribute("username", user.getUsername());
        model.addAttribute("securityQuestion", user.getSecurityQuestion());
        return "auth/reset-password";
    }

    /** Step 2: answer + new password. */
    @PostMapping("/forgot-password/reset")
    public String resetPassword(@RequestParam("username") String username,
                                @RequestParam("answer") String answer,
                                @RequestParam("newPassword") String newPassword,
                                @RequestParam("confirmPassword") String confirmPassword,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        String problem = userService.resetPassword(username, answer, newPassword, confirmPassword);
        if (problem != null) {
            AppUser user = userService.findByUsername(username).orElse(null);
            if (user == null) return "redirect:/forgot-password";
            model.addAttribute("username", user.getUsername());
            model.addAttribute("securityQuestion", user.getSecurityQuestion());
            model.addAttribute("errorMessage", problem);
            return "auth/reset-password";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Your password has been reset. Please log in.");
        return "redirect:/login";
    }
}
