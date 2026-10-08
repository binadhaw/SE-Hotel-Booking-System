package com.Reservation.Hotel.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/** A member of a hotel's staff, managed by the hotel's manager (proposal: "manage ... staff"). */
@Entity
@Table(name = "hotel_staff")
public class StaffMember {

    public static final List<String> ROLES = List.of("Front Desk", "Reservations", "Housekeeping", "Chef", "Kitchen Staff",
            "Waiter", "Concierge", "Spa Therapist", "Maintenance", "Security", "Driver", "Duty Manager");
    public static final List<String> SHIFTS = List.of("MORNING", "EVENING", "NIGHT", "ROTATING");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Name must be 100 characters or fewer")
    private String fullName;

    @NotBlank(message = "Choose a role")
    @Column(length = 40)
    private String role;

    @Email(message = "Enter a valid email address")
    @Size(max = 150)
    @Column(length = 255)
    @Convert(converter = com.Reservation.Hotel.security.EncryptedStringConverter.class)
    private String email;

    @Pattern(regexp = "^[+0-9 ()-]{7,20}$", message = "Enter a valid phone number")
    @Column(length = 255)
    @Convert(converter = com.Reservation.Hotel.security.EncryptedStringConverter.class)
    private String phone;

    @NotBlank(message = "Choose a shift")
    @Column(length = 20)
    private String shift = "MORNING";

    @NotNull(message = "Start date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    private boolean active = true;

    @Size(max = 255, message = "Notes must be 255 characters or fewer")
    private String notes;

    public StaffMember() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Hotel getHotel() { return hotel; }
    public void setHotel(Hotel hotel) { this.hotel = hotel; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getShift() { return shift; }
    public void setShift(String shift) { this.shift = shift; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getInitials() {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] p = fullName.trim().split("\\s+");
        return (p[0].substring(0, 1) + (p.length > 1 ? p[p.length - 1].substring(0, 1) : "")).toUpperCase();
    }
}
