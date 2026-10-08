package com.Reservation.Hotel.model;

import jakarta.persistence.*;

@Entity
@Table(name = "itinerary_plans")
public class ItineraryPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 1000)
    private String description;

    private int durationDays;
    private double estimatedCost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_profile_id")
    private AgentProfile agentProfile;

    public ItineraryPlan() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }

    public double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(double estimatedCost) { this.estimatedCost = estimatedCost; }

    public AgentProfile getAgentProfile() { return agentProfile; }
    public void setAgentProfile(AgentProfile agentProfile) { this.agentProfile = agentProfile; }
}