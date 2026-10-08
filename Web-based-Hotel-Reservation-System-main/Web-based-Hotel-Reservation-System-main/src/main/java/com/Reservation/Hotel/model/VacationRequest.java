package com.Reservation.Hotel.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vacation_requests")
public class VacationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userUsername; // User who made the request
    @Convert(converter = com.Reservation.Hotel.security.EncryptedStringConverter.class)
    private String userName;
    @Convert(converter = com.Reservation.Hotel.security.EncryptedStringConverter.class)
    private String userEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private AgentProfile agentProfile;

    @Column(length = 1000)
    private String userPreferences; // Requirements, budget, dates, desired activities

    // Statuses: PENDING_PROPOSAL, PROPOSED, ACCEPTED, REJECTED, RE_REQUESTED
    private String status = "PENDING_PROPOSAL";

    // Agent's response details
    @Column(length = 2000)
    private String proposedItineraryPlan;
    private double proposedPrice;

    @Column(length = 500)
    private String reRequestFeedback; // Feedback if user re-requests modifications

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;

    // Trip details (PBI-07)
    private java.time.LocalDate travelStart;
    private java.time.LocalDate travelEnd;
    private Integer travellers;
    private Double budget;

    // Optional: the hotel booking this trip is built around (reference, not a foreign key)
    @Column(length = 20)
    private String bookingReference;

    // Every version of the agent's plan, newest first (PBI-23)
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("version DESC")
    private List<ItineraryRevision> revisions = new ArrayList<>();

    public VacationRequest() {}

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public java.time.LocalDate getTravelStart() { return travelStart; }
    public void setTravelStart(java.time.LocalDate travelStart) { this.travelStart = travelStart; }

    public java.time.LocalDate getTravelEnd() { return travelEnd; }
    public void setTravelEnd(java.time.LocalDate travelEnd) { this.travelEnd = travelEnd; }

    public Integer getTravellers() { return travellers; }
    public void setTravellers(Integer travellers) { this.travellers = travellers; }

    public Double getBudget() { return budget; }
    public void setBudget(Double budget) { this.budget = budget; }

    public String getBookingReference() { return bookingReference; }
    public void setBookingReference(String bookingReference) { this.bookingReference = bookingReference; }

    public List<ItineraryRevision> getRevisions() { return revisions; }
    public void setRevisions(List<ItineraryRevision> revisions) { this.revisions = revisions; }

    /** Latest version of the agent's plan, or null before the first proposal. */
    public ItineraryRevision getCurrentRevision() {
        return revisions == null || revisions.isEmpty() ? null : revisions.get(0);
    }

    /** The agent may (re)send a plan while the tourist has not accepted or rejected it. */
    public boolean isOpenForProposal() {
        return "PENDING_PROPOSAL".equals(status) || "PROPOSED".equals(status) || "RE_REQUESTED".equals(status);
    }

    //Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserUsername() { return userUsername; }
    public void setUserUsername(String userUsername) { this.userUsername = userUsername; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public AgentProfile getAgentProfile() { return agentProfile; }
    public void setAgentProfile(AgentProfile agentProfile) { this.agentProfile = agentProfile; }

    public String getUserPreferences() { return userPreferences; }
    public void setUserPreferences(String userPreferences) { this.userPreferences = userPreferences; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProposedItineraryPlan() { return proposedItineraryPlan; }
    public void setProposedItineraryPlan(String proposedItineraryPlan) { this.proposedItineraryPlan = proposedItineraryPlan; }

    public double getProposedPrice() { return proposedPrice; }
    public void setProposedPrice(double proposedPrice) { this.proposedPrice = proposedPrice; }

    public String getReRequestFeedback() { return reRequestFeedback; }
    public void setReRequestFeedback(String reRequestFeedback) { this.reRequestFeedback = reRequestFeedback; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}