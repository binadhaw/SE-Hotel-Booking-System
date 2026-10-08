package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.model.AppUser;
import com.Reservation.Hotel.service.UserService;
import org.springframework.security.core.Authentication;
import com.Reservation.Hotel.service.ActivityLogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** PBI-15: administrators search, suspend/re-activate and delete user accounts. */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;

    private final ActivityLogService activityLog;

    public AdminUserController(UserService userService, ActivityLogService activityLog) {
        this.activityLog = activityLog;
        this.userService = userService;
    }

    @GetMapping
    public String listUsers(@RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "role", required = false, defaultValue = "ALL") String role,
                            Model model) {
        model.addAttribute("users", userService.search(q, role));
        model.addAttribute("q", q);
        model.addAttribute("currentRole", role.toUpperCase());
        model.addAttribute("countUsers", userService.countByRole(AppUser.ROLE_USER));
        model.addAttribute("countManagers", userService.countByRole(AppUser.ROLE_MANAGER));
        model.addAttribute("countAgents", userService.countByRole(AppUser.ROLE_AGENT));
        model.addAttribute("countAdmins", userService.countByRole(AppUser.ROLE_ADMIN));
        return "admin/users";
    }

    @PostMapping("/{id}/suspend")
    public String suspend(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        String problem = userService.setEnabled(id, false, authentication.getName());
        if (problem == null) activityLog.log(authentication.getName(), "USER_SUSPENDED", "User id " + id);
        flash(redirectAttributes, problem, "Account suspended.");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        String problem = userService.setEnabled(id, true, authentication.getName());
        if (problem == null) activityLog.log(authentication.getName(), "USER_ACTIVATED", "User id " + id);
        flash(redirectAttributes, problem, "Account re-activated.");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        String problem = userService.deleteUser(id, authentication.getName());
        if (problem == null) activityLog.log(authentication.getName(), "USER_DELETED", "User id " + id);
        flash(redirectAttributes, problem, "Account deleted.");
        return "redirect:/admin/users";
    }

    private void flash(RedirectAttributes redirectAttributes, String problem, String success) {
        if (problem != null) {
            redirectAttributes.addFlashAttribute("errorMessage", problem);
        } else {
            redirectAttributes.addFlashAttribute("successMessage", success);
        }
    }
}
