package com.Reservation.Hotel.repository;

import com.Reservation.Hotel.model.StaffMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StaffRepository extends JpaRepository<StaffMember, Long> {

    List<StaffMember> findByHotelIdOrderByActiveDescFullNameAsc(Long hotelId);

    long countByHotelIdAndActiveTrue(Long hotelId);

    @Modifying
    @Query("delete from StaffMember s where s.hotel.id = :hotelId")
    int deleteByHotelId(@Param("hotelId") Long hotelId);
}
