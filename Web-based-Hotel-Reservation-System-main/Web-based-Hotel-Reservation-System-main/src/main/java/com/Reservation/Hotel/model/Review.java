package com.Reservation.Hotel.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A guest's rating and review of a hotel after a completed stay (one review per booking). */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", unique = true)
    private Booking booking;

    @Column(nullable = false, length = 50)
    private String username;

    // 1..5 stars
    private int rating;

    @Column(length = 120)
    private String title;

    @Column(length = 2000)
    private String comment;

    // Hotel manager's public reply (PBI-20)
    @Column(length = 1000)
    private String managerReply;
    private LocalDateTime repliedAt;

    // Hidden by an administrator (inappropriate content) - kept for the record but not shown or counted
    private boolean hidden = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Review() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Hotel getHotel() { return hotel; }
    public void setHotel(Hotel hotel) { this.hotel = hotel; }

    public Booking getBooking() { return booking; }
    public void setBooking(Booking booking) { this.booking = booking; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getManagerReply() { return managerReply; }
    public void setManagerReply(String managerReply) { this.managerReply = managerReply; }

    public LocalDateTime getRepliedAt() { return repliedAt; }
    public void setRepliedAt(LocalDateTime repliedAt) { this.repliedAt = repliedAt; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    /** Days after posting during which the guest may still edit or delete the review. */
    public static final int EDIT_WINDOW_DAYS = 14;

    /**
     * A review can be changed only for a while after posting, and never once the hotel has replied publicly -
     * otherwise the reply could end up answering something the guest no longer says.
     */
    public boolean isEditable() {
        return managerReply == null && createdAt != null && createdAt.isAfter(LocalDateTime.now().minusDays(EDIT_WINDOW_DAYS));
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
