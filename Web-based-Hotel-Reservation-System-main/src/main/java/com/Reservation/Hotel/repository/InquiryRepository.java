package com.Reservation.Hotel.repository;

import com.Reservation.Hotel.model.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
    List<Inquiry> findByCreatedWithUsername(String createdWithUsername);
    List<Inquiry> findByTargetRole(String targetRole);

    /** Inquiries about the hotels a manager owns, plus older manager inquiries that were not tied to a hotel. */
    @org.springframework.data.jpa.repository.Query("select i from Inquiry i left join i.hotel h where i.targetRole = 'MANAGER' " +
            "and (h.managerUsername = :manager or h is null) order by i.createdAt desc")
    List<Inquiry> findForManager(@org.springframework.data.repository.query.Param("manager") String manager);

    long countByStatus(String status);
}