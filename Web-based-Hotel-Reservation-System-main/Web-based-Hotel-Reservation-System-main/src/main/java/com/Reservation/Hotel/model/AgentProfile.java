package com.Reservation.Hotel.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "agent_profiles")
public class AgentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username; // Logged-in agent's username

    @NotBlank(message = "Agency / Full Name is required")
    @Size(max = 100, message = "Agency name must be 100 characters or fewer")
    private String agencyName;

    @NotBlank(message = "Contact phone is required")
    @Pattern(regexp = "^[+0-9 ()-]{7,20}$", message = "Enter a valid phone number")
    @Convert(converter = com.Reservation.Hotel.security.EncryptedStringConverter.class)
    private String phone;

    @Min(value = 0, message = "Experience cannot be negative")
    @Max(value = 60, message = "Experience must be 60 years or fewer")
    private int experienceYears;

    @Size(max = 255, message = "Specializations must be 255 characters or fewer")
    private String specializations;

    @Size(max = 1000, message = "Local experiences must be 1000 characters or fewer")
    @Column(length = 1000)
    private String localExperiences;

    // Public "about me" text for the agent's profile page (PBI-24)
    @Size(max = 1000, message = "Bio must be 1000 characters or fewer")
    @Column(length = 1000)
    private String bio;

    @Pattern(regexp = "^$|^https?://\\S{3,250}$", message = "Documents link must start with http:// or https://")
    private String documentsSubmittedUrl; // Link/Info to submitted verification documents

    // Admin Approval Statuses: PENDING, APPROVED, REJECTED, NEEDS_INFO
    private String status = "PENDING";

    @Column(length = 500)
    private String adminNote; // Feedback from Admin if details/docs are missing

    // Performance & Gamified Ranking Metrics
    private int completedJobs = 0;
    private int rewardPoints = 0; // Points earned by accepted plans
    private double rankingScore = 0.0;

    @OneToMany(mappedBy = "agentProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItineraryPlan> sampleItineraries = new ArrayList<>();

    public AgentProfile() {}

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAgencyName() { return agencyName; }
    public void setAgencyName(String agencyName) { this.agencyName = agencyName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public int getExperienceYears() { return experienceYears; }
    public void setExperienceYears(int experienceYears) { this.experienceYears = experienceYears; }

    public String getSpecializations() { return specializations; }
    public void setSpecializations(String specializations) { this.specializations = specializations; }

    public String getLocalExperiences() { return localExperiences; }
    public void setLocalExperiences(String localExperiences) { this.localExperiences = localExperiences; }

    public String getDocumentsSubmittedUrl() { return documentsSubmittedUrl; }
    public void setDocumentsSubmittedUrl(String documentsSubmittedUrl) { this.documentsSubmittedUrl = documentsSubmittedUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }

    public int getCompletedJobs() { return completedJobs; }
    public void setCompletedJobs(int completedJobs) { this.completedJobs = completedJobs; }

    public int getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(int rewardPoints) { this.rewardPoints = rewardPoints; }

    public double getRankingScore() { return rankingScore; }
    public void setRankingScore(double rankingScore) { this.rankingScore = rankingScore; }

    public List<ItineraryPlan> getSampleItineraries() { return sampleItineraries; }
    public void setSampleItineraries(List<ItineraryPlan> sampleItineraries) { this.sampleItineraries = sampleItineraries; }

    public void addItinerary(ItineraryPlan plan) {
        sampleItineraries.add(plan);
        plan.setAgentProfile(this);
    }
}