package com.Reservation.Hotel.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A registered account. The role is one of USER (tourist), MANAGER (hotel manager),
 * AGENT (travel agent) or ADMIN - the same names used by hasRole(...) in SecurityConfig.
 */
@Entity
@Table(name = "users")
public class AppUser {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_MANAGER = "MANAGER";
    public static final String ROLE_AGENT = "AGENT";
    public static final String ROLE_ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password; // BCrypt hash

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 255)
    @Convert(converter = com.Reservation.Hotel.security.EncryptedStringConverter.class)
    private String phone;

    @Column(nullable = false, length = 20)
    private String role = ROLE_USER;

    // false = suspended by an admin; Spring Security refuses the login
    private boolean enabled = true;

    // Used by the "forgot password" flow (answer stored as a BCrypt hash, compared case-insensitively)
    @Column(length = 200)
    private String securityQuestion;
    private String securityAnswer;

    // Tourist opts in to promotional discount alerts (Phase 6)
    private boolean promoAlerts = true;

    // Travel preferences (Phase 13.3) - used to target offers and recommend stays
    @Column(length = 300)
    private String travelInterests;        // comma separated attraction categories, e.g. "Beach,Wildlife"
    @Column(length = 80)
    private String preferredDestination;   // e.g. "Galle"
    private Double budgetPerNight;         // USD

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lastLoginAt;

    public AppUser() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getSecurityQuestion() { return securityQuestion; }
    public void setSecurityQuestion(String securityQuestion) { this.securityQuestion = securityQuestion; }

    public String getSecurityAnswer() { return securityAnswer; }
    public void setSecurityAnswer(String securityAnswer) { this.securityAnswer = securityAnswer; }

    public boolean isPromoAlerts() { return promoAlerts; }
    public void setPromoAlerts(boolean promoAlerts) { this.promoAlerts = promoAlerts; }

    public String getTravelInterests() { return travelInterests; }
    public void setTravelInterests(String travelInterests) { this.travelInterests = travelInterests; }
    public String getPreferredDestination() { return preferredDestination; }
    public void setPreferredDestination(String preferredDestination) { this.preferredDestination = preferredDestination; }
    public Double getBudgetPerNight() { return budgetPerNight; }
    public void setBudgetPerNight(Double budgetPerNight) { this.budgetPerNight = budgetPerNight; }

    public java.util.List<String> getInterestList() {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (travelInterests == null) return list;
        for (String s : travelInterests.split(",")) if (!s.isBlank()) list.add(s.trim());
        return list;
    }

    public boolean hasPreferences() {
        return !getInterestList().isEmpty() || (preferredDestination != null && !preferredDestination.isBlank()) || budgetPerNight != null;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    /** Friendly role label for the UI. */
    public String getRoleLabel() {
        return switch (role == null ? "" : role) {
            case ROLE_MANAGER -> "Hotel Manager";
            case ROLE_AGENT -> "Travel Agent";
            case ROLE_ADMIN -> "Administrator";
            default -> "Tourist";
        };
    }
}
