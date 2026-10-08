package com.Reservation.Hotel.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One version of an agent's itinerary proposal for a vacation request. Every time the agent sends or
 * updates the plan a new revision is stored, so the tourist can see what changed (PBI-23).
 */
@Entity
@Table(name = "itinerary_revisions")
public class ItineraryRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private VacationRequest request;

    private int version;

    @Column(length = 4000)
    private String plan;

    private double price;

    // What the agent changed in this version (e.g. "Added a whale-watching morning as requested")
    @Column(length = 500)
    private String changeNote;

    // The traveller's change request this version answers (null for the first version)
    @Column(length = 500)
    private String requestedChanges;

    // Attractions the agent recommends in this version (PBI-22)
    @ManyToMany
    @JoinTable(name = "itinerary_revision_attractions",
            joinColumns = @JoinColumn(name = "revision_id"),
            inverseJoinColumns = @JoinColumn(name = "attraction_id"))
    private List<Attraction> attractions = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();

    public ItineraryRevision() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public VacationRequest getRequest() { return request; }
    public void setRequest(VacationRequest request) { this.request = request; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String changeNote) { this.changeNote = changeNote; }

    public String getRequestedChanges() { return requestedChanges; }
    public void setRequestedChanges(String requestedChanges) { this.requestedChanges = requestedChanges; }

    public List<Attraction> getAttractions() { return attractions; }
    public void setAttractions(List<Attraction> attractions) { this.attractions = attractions; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
