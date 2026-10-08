package com.Reservation.Hotel.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotions")
public class Promotion {

    public enum PromotionType {
        COUPON, DISCOUNT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private PromotionType type = PromotionType.COUPON;

    // Made nullable and removed unique constraint to support standard discounts
    @Column(nullable = true)
    private String code;

    private String description;
    private Double discountPercentage;

    private boolean active = true;

    // Recurring Flash Window Settings (Used for COUPON type)
    private Integer intervalMinutes;
    private Integer durationMinutes;

    // Fixed Date Window Settings (Used for DISCOUNT type & standard validity)
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;

    // null = platform-wide offer (admin); otherwise the offer only applies to this hotel (its manager)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    // Terms shown on the coupon and in the coupon email
    @Column(length = 500)
    private String terms;

    // Who created it (manager or admin username)
    @Column(length = 50)
    private String createdBy;

    public Promotion() {}

    public Hotel getHotel() { return hotel; }
    public void setHotel(Hotel hotel) { this.hotel = hotel; }

    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    /** "All hotels" or the hotel name. */
    public String getScopeLabel() {
        return hotel == null ? "All hotels" : hotel.getName();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PromotionType getType() { return type; }
    public void setType(PromotionType type) { this.type = type; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(Double discountPercentage) { this.discountPercentage = discountPercentage; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getIntervalMinutes() { return intervalMinutes; }
    public void setIntervalMinutes(Integer intervalMinutes) { this.intervalMinutes = intervalMinutes; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }

    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime validUntil) { this.validUntil = validUntil; }
}