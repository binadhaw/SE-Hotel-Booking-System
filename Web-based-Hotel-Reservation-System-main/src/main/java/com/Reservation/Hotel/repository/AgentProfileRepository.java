package com.Reservation.Hotel.repository;

import com.Reservation.Hotel.model.AgentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentProfileRepository extends JpaRepository<AgentProfile, Long> {
    Optional<AgentProfile> findByUsername(String username);
    List<AgentProfile> findByStatus(String status);
    List<AgentProfile> findByStatusOrderByRankingScoreDesc(String status);
}