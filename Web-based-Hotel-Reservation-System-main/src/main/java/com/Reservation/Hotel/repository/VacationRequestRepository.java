package com.Reservation.Hotel.repository;

import com.Reservation.Hotel.model.VacationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VacationRequestRepository extends JpaRepository<VacationRequest, Long> {

    // Methods required by AgentService.java
    List<VacationRequest> findByAgentProfileUsername(String agentUsername);
    List<VacationRequest> findByUserUsername(String userUsername);

    // Count methods required by AgentManagementController for Performance Metrics
    long countByStatus(String status);
    long countByAgentProfileUsernameAndStatus(String agentUsername, String status);

    // Used by AgentService.deleteAgent() to clear out an agent's vacation requests first -
    // otherwise the FK on vacation_requests.agent_id blocks deleting the agent_profiles row.
    void deleteByAgentProfileId(Long agentProfileId);
}