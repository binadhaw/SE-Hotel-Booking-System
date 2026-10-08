package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Edit profile (name, email, phone) and change password - available to every role. */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String showProfile(Authentication authentication, Model model) {
        if (userService.findByUsername(authentication.getName()).isEmpty()) {
            return "redirect:/login";
        }
        model.addAttribute("interestOptions", com.Reservation.Hotel.model.Attraction.CATEGORIES);
        return "profile/index";
    }

    @PostMapping
    public String updateProfile(@RequestParam("fullName") String fullName,
                                @RequestParam("email") String email,
                                @RequestParam(value = "phone", required = false) String phone,
                                @RequestParam(value = "promoAlerts", defaultValue = "false") boolean promoAlerts,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        String problem = userService.updateProfile(authentication.getName(), fullName, email, phone, promoAlerts);
        if (problem != null) {
            redirectAttributes.addFlashAttribute("errorMessage", problem);
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Profile updated.");
        }
        return "redirect:/profile";
    }

    @PostMapping("/preferences")
    public String updatePreferences(@RequestParam(value = "interests", required = false) java.util.List<String> interests,
                                    @RequestParam(value = "preferredDestination", required = false) String destination,
                                    @RequestParam(value = "budgetPerNight", required = false) Double budget,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        String problem = userService.updatePreferences(authentication.getName(), interests, destination, budget);
        if (problem != null) redirectAttributes.addFlashAttribute("errorMessage", problem);
        else redirectAttributes.addFlashAttribute("successMessage", "Travel preferences saved - we'll tailor offers and suggestions to you.");
        return "redirect:/profile#preferences";
    }

    @PostMapping("/password")
    public String changePassword(@RequestParam("currentPassword") String currentPassword,
                                 @RequestParam("newPassword") String newPassword,
                                 @RequestParam("confirmPassword") String confirmPassword,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        String problem = userService.changePassword(authentication.getName(), currentPassword, newPassword, confirmPassword);
        if (problem != null) {
            redirectAttributes.addFlashAttribute("passwordError", problem);
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully.");
        }
        return "redirect:/profile";
    }
}
