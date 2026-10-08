package com.Reservation.Hotel.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Audit trail of important platform actions, shown on the admin dashboard (PBI-14). */
@Entity
@Table(name = "activity_log", indexes = @Index(name = "idx_activity_time", columnList = "createdAt"))
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String actor;

    // e.g. HOTEL_APPROVED, USER_SUSPENDED, BOOKING_CREATED
    @Column(nullable = false, length = 40)
    private String action;

    @Column(length = 500)
    private String details;

    private LocalDateTime createdAt = LocalDateTime.now();

    public ActivityLog() {}

    public ActivityLog(String actor, String action, String details) {
        this.actor = actor;
        this.action = action;
        this.details = details;
    }

    public Long getId() { return id; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** "HOTEL_APPROVED" -> "Hotel approved" */
    public String getActionLabel() {
        String s = action.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Bootstrap icon for the action family. */
    public String getIcon() {
        if (action.startsWith("HOTEL")) return "bi-building";
        if (action.startsWith("USER")) return "bi-person";
        if (action.startsWith("BOOKING")) return "bi-calendar-check";
        if (action.startsWith("AGENT")) return "bi-compass";
        if (action.startsWith("REVIEW")) return "bi-star";
        if (action.startsWith("PROMOTION")) return "bi-ticket-perforated";
        return "bi-activity";
    }
}
