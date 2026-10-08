package com.Reservation.Hotel.repository;

import com.Reservation.Hotel.model.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findTop15ByOrderByCreatedAtDesc();

    Page<ActivityLog> findByActionStartingWithOrderByCreatedAtDesc(String prefix, Pageable pageable);
}
