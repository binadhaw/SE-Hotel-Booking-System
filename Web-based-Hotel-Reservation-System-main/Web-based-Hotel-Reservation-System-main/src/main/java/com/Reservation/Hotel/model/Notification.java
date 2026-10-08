package com.Reservation.Hotel.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** In-app alert shown in the navbar bell (booking updates, replies, promotions...). */
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notification_user", columnList = "username, readFlag"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String message;

    // App-relative link to the related page, e.g. /bookings
    @Column(length = 300)
    private String link;

    private boolean readFlag = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification() {}

    public Notification(String username, String title, String message, String link) {
        this.username = username;
        this.title = title;
        this.message = message;
        this.link = link;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public boolean isReadFlag() { return readFlag; }
    public void setReadFlag(boolean readFlag) { this.readFlag = readFlag; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
