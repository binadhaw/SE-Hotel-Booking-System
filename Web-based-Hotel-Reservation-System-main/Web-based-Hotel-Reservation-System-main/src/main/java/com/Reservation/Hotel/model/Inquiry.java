package com.Reservation.Hotel.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
@Table(name = "inquiries")
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be 100 characters or fewer")
    @Convert(converter = com.Reservation.Hotel.security.EncryptedStringConverter.class)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Convert(converter = com.Reservation.Hotel.security.EncryptedStringConverter.class)
    private String email;

    @NotBlank(message = "Subject is required")
    @Size(min = 3, max = 150, message = "Subject must be 3-150 characters")
    private String subject;

    @NotBlank(message = "Message is required")
    @Size(min = 10, max = 1000, message = "Message must be 10-1000 characters")
    @Column(length = 1000)
    private String message;

    private String priority = "MEDIUM"; // HIGH, MEDIUM, LOW

    private String senderRole; // USER, MANAGER, AGENT
    private String targetRole; // MANAGER, ADMIN

    private String createdWithUsername; // Stores logged-in username

    private String response;
    private String status = "PENDING"; // PENDING, RESOLVED, IGNORED

    private LocalDateTime createdAt = LocalDateTime.now();

    // Support ticket number shown to the tourist, e.g. TCK-4F1A2B
    @Column(length = 20)
    private String ticketNumber;

    // Hotel the inquiry is about (null = general platform support, answered by an administrator)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @Column(length = 50)
    private String respondedBy;
    private LocalDateTime respondedAt;

    public Inquiry() {}

    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }

    public Hotel getHotel() { return hotel; }
    public void setHotel(Hotel hotel) { this.hotel = hotel; }

    public String getRespondedBy() { return respondedBy; }
    public void setRespondedBy(String respondedBy) { this.respondedBy = respondedBy; }

    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }

    /** Ticket number, or the id for inquiries created before ticket numbers existed. */
    public String getDisplayTicket() {
        return ticketNumber != null ? ticketNumber : "#" + id;
    }

    /** Who the inquiry is addressed to, for the UI. */
    public String getRecipientLabel() {
        return hotel != null ? hotel.getName() : ("ADMIN".equals(targetRole) ? "Platform support" : "Hotel management");
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }

    public String getCreatedWithUsername() { return createdWithUsername; }
    public void setCreatedWithUsername(String createdWithUsername) { this.createdWithUsername = createdWithUsername; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}