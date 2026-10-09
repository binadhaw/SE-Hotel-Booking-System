package com.Reservation.Hotel.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Room number is required")
    @Size(max = 20, message = "Room number must be 20 characters or fewer")
    private String roomNumber;

    @NotBlank(message = "Room type is required")
    @Size(max = 60, message = "Room type must be 60 characters or fewer")
    private String roomType; // e.g., Standard, Deluxe, Suite

    @NotNull(message = "Price per night is required")
    @Positive(message = "Price must be greater than zero")
    @Max(value = 100000, message = "Price looks too high")
    private Double price;

    private boolean available = true;

    // Number of guests the room is recommended for (a recommendation, not a hard limit)
    @NotNull(message = "Recommended guest count is required")
    @Min(value = 1, message = "At least 1 guest")
    @Max(value = 20, message = "Maximum 20 guests")
    private Integer maxGuests = 2;

    // Bed details, e.g. 2 x Queen
    @NotNull(message = "Number of beds is required")
    @Min(value = 1, message = "At least 1 bed")
    @Max(value = 10, message = "Maximum 10 beds")
    private Integer bedCount = 1;

    @NotBlank(message = "Bed type is required")
    private String bedType = "Double";

    // Public URL paths of the uploaded room photos, e.g. /uploads/rooms/<uuid>.jpg
    // The first photo is the cover image. Stored in its own table "room_images".
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "room_images", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "image_path", length = 500)
    private List<String> imagePaths = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    // ---- Not stored in the database: filled in by RoomService from the APPROVED bookings ----
    // reserved      = an approved booking currently holds this room (until its check-out date)
    // reservedUntil = that booking's check-out date
    // reservedBookingId / reservedGuest = which booking, so the manager can release the room early
    @Transient
    private boolean reserved;
    @Transient
    private LocalDate reservedUntil;
    @Transient
    private Long reservedBookingId;
    @Transient
    private String reservedGuest;

    public Room() {}

    public Room(String roomNumber, String roomType, Double price, boolean available, Hotel hotel) {
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.price = price;
        this.available = available;
        this.hotel = hotel;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public Integer getMaxGuests() { return maxGuests; }
    public void setMaxGuests(Integer maxGuests) { this.maxGuests = maxGuests; }

    public Integer getBedCount() { return bedCount; }
    public void setBedCount(Integer bedCount) { this.bedCount = bedCount; }

    public String getBedType() { return bedType; }
    public void setBedType(String bedType) { this.bedType = bedType; }

    public List<String> getImagePaths() { return imagePaths; }
    public void setImagePaths(List<String> imagePaths) { this.imagePaths = imagePaths; }

    public Hotel getHotel() { return hotel; }
    public void setHotel(Hotel hotel) { this.hotel = hotel; }

    public boolean isReserved() { return reserved; }
    public void setReserved(boolean reserved) { this.reserved = reserved; }

    public LocalDate getReservedUntil() { return reservedUntil; }
    public void setReservedUntil(LocalDate reservedUntil) { this.reservedUntil = reservedUntil; }

    public Long getReservedBookingId() { return reservedBookingId; }
    public void setReservedBookingId(Long reservedBookingId) { this.reservedBookingId = reservedBookingId; }

    public String getReservedGuest() { return reservedGuest; }
    public void setReservedGuest(String reservedGuest) { this.reservedGuest = reservedGuest; }

    /** Can a guest book this room right now? (manager left it available AND nobody holds it) */
    public boolean isBookableByGuests() { return available && !reserved; }

    /** Label shown to managers / admins: Reserved, Available or Unavailable (manually switched off). */
    public String getDisplayStatus() {
        if (reserved) return "Reserved";
        return available ? "Available" : "Unavailable";
    }

    /** First uploaded photo (used as thumbnail), or null when the room has no photos. */
    public String getCoverImage() {
        return (imagePaths != null && !imagePaths.isEmpty()) ? imagePaths.get(0) : null;
    }

    /** Human readable bed description, e.g. "2 Queen beds" or "1 King bed". */
    public String getBedSummary() {
        if (bedCount == null || bedType == null || bedType.isBlank()) return "Not set";
        return bedCount + " " + bedType + (bedCount == 1 ? " bed" : " beds");
    }
}