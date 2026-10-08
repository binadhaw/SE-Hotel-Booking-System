package com.Reservation.Hotel.service;

import com.Reservation.Hotel.model.Notification;
import com.Reservation.Hotel.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Single entry point for telling a user that something happened (booking approved, hotel
 * rejected, inquiry answered...). Every notification lands in the user's in-app inbox (navbar bell);
 * {@link #notify} also emails it to their registered address.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    public NotificationService(NotificationRepository notificationRepository, EmailService emailService) {
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
    }

    /**
     * In-app notification plus email.
     * @param username recipient
     * @param title    short headline, also the email subject
     * @param message  plain text body (escaped before it goes into the email)
     * @param link     app-relative link to the related page, or null
     */
    public void notify(String username, String title, String message, String link) {
        if (username == null) return;
        notifyInApp(username, title, message, link);
        emailService.sendToUser(username, title, "<p>" + EmailService.esc(message) + "</p>");
    }

    /** In-app notification only (no email). */
    public void notifyInApp(String username, String title, String message, String link) {
        if (username == null) return;
        notificationRepository.save(new Notification(username, truncate(title, 200), truncate(message, 1000), link));
    }

    public List<Notification> latest(String username) {
        return notificationRepository.findTop50ByUsernameOrderByCreatedAtDesc(username);
    }

    public List<Notification> preview(String username) {
        return notificationRepository.findTop5ByUsernameOrderByCreatedAtDesc(username);
    }

    public long unreadCount(String username) {
        return notificationRepository.countByUsernameAndReadFlagFalse(username);
    }

    /** Deletes one of the user's own notifications. */
    @Transactional
    public void delete(Long id, String username) {
        notificationRepository.findById(id)
                .filter(n -> n.getUsername().equals(username))
                .ifPresent(notificationRepository::delete);
    }

    /** Deletes all of the user's notifications that were already read. */
    @Transactional
    public void clearRead(String username) {
        notificationRepository.deleteByUsernameAndReadFlagTrue(username);
    }

    @Transactional
    public void markAllRead(String username) {
        notificationRepository.markAllRead(username);
    }

    /** Marks one notification read and returns its link (only for the owner), or null. */
    @Transactional
    public String open(Long id, String username) {
        Notification n = notificationRepository.findById(id).orElse(null);
        if (n == null || !n.getUsername().equals(username)) return null;
        n.setReadFlag(true);
        notificationRepository.save(n);
        return n.getLink();
    }

    private static String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
