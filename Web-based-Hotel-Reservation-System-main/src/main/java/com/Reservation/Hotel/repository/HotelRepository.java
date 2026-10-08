package com.Reservation.Hotel.repository;

import com.Reservation.Hotel.model.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long> {

    List<Hotel> findByStatus(String status);

    List<Hotel> findByManagerUsername(String managerUsername);

    List<Hotel> findByManagerUsernameIsNull();

    long countByStatus(String status);
}
