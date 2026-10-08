package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** The notification centre behind the navbar bell. */
@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public String list(Authentication authentication, Model model) {
        model.addAttribute("notifications", notificationService.latest(authentication.getName()));
        return "notifications/index";
    }

    /** Marks the notification read and jumps to the page it is about. */
    @GetMapping("/{id}/open")
    public String open(@PathVariable Long id, Authentication authentication) {
        String link = notificationService.open(id, authentication.getName());
        // Only follow app-relative links (never an external redirect)
        if (link == null || !link.startsWith("/") || link.startsWith("//")) return "redirect:/notifications";
        return "redirect:" + link;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication) {
        notificationService.delete(id, authentication.getName());
        return "redirect:/notifications";
    }

    @PostMapping("/clear-read")
    public String clearRead(Authentication authentication) {
        notificationService.clearRead(authentication.getName());
        return "redirect:/notifications";
    }

    @PostMapping("/read-all")
    public String readAll(Authentication authentication) {
        notificationService.markAllRead(authentication.getName());
        return "redirect:/notifications";
    }
}
