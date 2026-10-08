package com.Reservation.Hotel.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hotels")
public class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Hotel name is required")
    @Size(min = 2, max = 100, message = "Hotel name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Location is required")
    @Size(max = 100, message = "Location must be 100 characters or fewer")
    private String location;

    // PENDING (awaiting admin approval), APPROVED (live), REJECTED (admin declined), REMOVED (taken off the platform)
    private String status = "PENDING";

    // Reason shown to the manager when the hotel was rejected or removed
    @Column(length = 500)
    private String statusNote;

    // Username of the hotel manager who owns this property
    @Column(length = 50)
    private String managerUsername;

    @Column(length = 30)
    @Pattern(regexp = "^$|^[+0-9 ()-]{7,20}$", message = "Enter a valid phone number")
    private String contactPhone;

    @Column(length = 150)
    @Email(message = "Enter a valid email address")
    private String contactEmail;

    private LocalDateTime createdAt;

    @Size(max = 255, message = "Description must be 255 characters or fewer")
    private String description;

    // Comma separated list of the amenities the manager ticked, e.g. "Free WiFi,Swimming Pool"
    @Column(length = 1000)
    private String amenities;

    // Public URL paths of the uploaded hotel photos, e.g. /uploads/hotels/<uuid>.jpg
    // The first photo is used as the cover image. Stored in its own table "hotel_images".
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "hotel_images", joinColumns = @JoinColumn(name = "hotel_id"))
    @Column(name = "image_path", length = 500)
    private List<String> imagePaths = new ArrayList<>();

    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Booking> bookings;

    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Room> rooms;

    public List<Room> getRooms() { return rooms; }
    public void setRooms(List<Room> rooms) { this.rooms = rooms; }

    public Hotel() {}

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String REMOVED = "REMOVED";

    public String getStatusNote() { return statusNote; }
    public void setStatusNote(String statusNote) { this.statusNote = statusNote; }

    public String getManagerUsername() { return managerUsername; }
    public void setManagerUsername(String managerUsername) { this.managerUsername = managerUsername; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isApproved() { return APPROVED.equals(status); }

    /** Amenities as a list (stored comma separated). */
    public List<String> getAmenityList() {
        List<String> list = new ArrayList<>();
        if (amenities == null) return list;
        for (String a : amenities.split(",")) {
            if (!a.isBlank()) list.add(a.trim());
        }
        return list;
    }

    /** Lowest room price, or null when the hotel has no priced rooms. */
    public Double getFromPrice() {
        if (rooms == null) return null;
        return rooms.stream().filter(r -> r.getPrice() != null && r.isAvailable())
                .map(Room::getPrice).min(Double::compare).orElse(null);
    }

    public Hotel(String name, String location, String status, String description, String amenities) {
        this.name = name;
        this.location = location;
        this.status = status;
        this.description = description;
        this.amenities = amenities;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAmenities() { return amenities; }
    public void setAmenities(String amenities) { this.amenities = amenities; }

    public List<String> getImagePaths() { return imagePaths; }
    public void setImagePaths(List<String> imagePaths) { this.imagePaths = imagePaths; }

    /** First uploaded photo (used as thumbnail), or null when the hotel has no photos. */
    public String getCoverImage() {
        return (imagePaths != null && !imagePaths.isEmpty()) ? imagePaths.get(0) : null;
    }

    public List<Booking> getBookings() { return bookings; }
    public void setBookings(List<Booking> bookings) { this.bookings = bookings; }

    public int getBookingCount() {
        return bookings != null ? bookings.size() : 0;
    }
}