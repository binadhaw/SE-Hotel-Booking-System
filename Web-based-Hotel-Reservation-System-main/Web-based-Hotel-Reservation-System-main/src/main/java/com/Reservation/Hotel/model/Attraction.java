package com.Reservation.Hotel.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

/**
 * A local attraction or experience (PBI-03 / PBI-22). It belongs to a town/city and can optionally be
 * pinned to a hotel with its distance, so the hotel page can list what is nearby.
 */
@Entity
@Table(name = "attractions")
public class Attraction {

    public static final java.util.List<String> CATEGORIES = java.util.List.of(
            "Nature", "Beach", "Culture & Heritage", "Wildlife", "Adventure", "Food & Drink", "Shopping", "Wellness");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 120)
    private String name;

    @NotBlank(message = "Category is required")
    @Column(length = 40)
    private String category;

    @NotBlank(message = "Town / city is required")
    @Size(max = 80)
    private String city;

    @Size(max = 1000)
    @Column(length = 1000)
    private String description;

    // Approximate cost per person in USD (0 = free)
    @NotNull(message = "Estimated cost is required")
    @Min(value = 0, message = "Cost cannot be negative")
    private Double estimatedCost = 0.0;

    // Optional: the hotel this is close to, and how far away it is
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @Min(value = 0, message = "Distance cannot be negative")
    private Double distanceKm;

    // Username of the travel agent / manager who recommended it ("system" for seeded data)
    @Column(length = 50)
    private String createdBy;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Attraction() {}

    public Attraction(String name, String category, String city, String description, double estimatedCost) {
        this.name = name;
        this.category = category;
        this.city = city;
        this.description = description;
        this.estimatedCost = estimatedCost;
        this.createdBy = "system";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(Double estimatedCost) { this.estimatedCost = estimatedCost; }

    public Hotel getHotel() { return hotel; }
    public void setHotel(Hotel hotel) { this.hotel = hotel; }

    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isFree() {
        return estimatedCost == null || estimatedCost <= 0;
    }

    public String getCategoryIcon() {
        return switch (category == null ? "" : category) {
            case "Nature" -> "bi-tree";
            case "Beach" -> "bi-umbrella";
            case "Culture & Heritage" -> "bi-bank";
            case "Wildlife" -> "bi-binoculars";
            case "Adventure" -> "bi-compass";
            case "Food & Drink" -> "bi-cup-hot";
            case "Shopping" -> "bi-bag";
            case "Wellness" -> "bi-flower1";
            default -> "bi-geo-alt";
        };
    }
}
