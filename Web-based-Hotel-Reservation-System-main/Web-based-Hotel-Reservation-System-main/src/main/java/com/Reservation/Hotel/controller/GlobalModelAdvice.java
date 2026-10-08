package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.model.AppUser;
import com.Reservation.Hotel.model.Notification;
import com.Reservation.Hotel.service.NotificationService;
import com.Reservation.Hotel.service.UserService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.beans.PropertyEditorSupport;
import java.util.List;

/** Values every page (mainly the navbar) needs. */
@ControllerAdvice
public class GlobalModelAdvice {

    private final UserService userService;
    private final NotificationService notificationService;

    public GlobalModelAdvice(UserService userService, NotificationService notificationService) {
        this.userService = userService;
        this.notificationService = notificationService;
    }

    private static boolean loggedIn(Authentication authentication) {
        return authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
    }

    @ModelAttribute("unreadNotifications")
    public long unreadNotifications(Authentication authentication) {
        return loggedIn(authentication) ? notificationService.unreadCount(authentication.getName()) : 0;
    }

    @ModelAttribute("notificationPreview")
    public List<Notification> notificationPreview(Authentication authentication) {
        return loggedIn(authentication) ? notificationService.preview(authentication.getName()) : List.of();
    }

    /**
     * Blank text inputs are bound as null instead of "" so optional fields really are empty
     * (otherwise templates show empty icons/labels and the database stores meaningless "").
     * Non-blank values are left exactly as typed.
     */
    @InitBinder
    public void blankStringsAsNull(WebDataBinder binder) {
        // Only for form objects (@ModelAttribute); plain @RequestParam values keep their own blank-checks.
        // Spring 6.1 creates form objects after the binder, so check the target *type*, not the target instance.
        if (binder.getTarget() == null && binder.getTargetType() == null) return;
        binder.registerCustomEditor(String.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text == null || text.isBlank() ? null : text);
            }
        });
    }

    @ModelAttribute("currentUser")
    public AppUser currentUser(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) return null;
        return userService.findByUsername(authentication.getName()).orElse(null);
    }
}
