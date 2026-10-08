package com.Reservation.Hotel.repository;

import com.Reservation.Hotel.model.Attraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttractionRepository extends JpaRepository<Attraction, Long> {

    List<Attraction> findAllByOrderByCityAscNameAsc();

    List<Attraction> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    List<Attraction> findByHotelId(Long hotelId);

    long count();

    /** Removes the attraction from any itinerary versions that recommended it (join table rows). */
    @Modifying
    @Query(value = "delete from itinerary_revision_attractions where attraction_id = :id", nativeQuery = true)
    int unlinkFromItineraries(@Param("id") Long id);

    /** Unpins attractions from a hotel that is being deleted (they stay listed under their town). */
    @Modifying
    @Query("update Attraction a set a.hotel = null, a.distanceKm = null where a.hotel.id = :hotelId")
    int detachFromHotel(@Param("hotelId") Long hotelId);
}
