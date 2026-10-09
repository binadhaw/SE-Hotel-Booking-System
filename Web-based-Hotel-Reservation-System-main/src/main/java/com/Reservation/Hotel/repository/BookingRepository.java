package com.Reservation.Hotel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUsername(String username);

    List<Booking> findByHotelId(Long hotelId);

    boolean existsByRoomId(Long roomId);

    List<Booking> findByHotelManagerUsername(String managerUsername);

    /*
     * "Active reservation" = an APPROVED booking whose room has not been released early
     * and whose check-out date has not passed yet. This is the same rule as Booking.isRoomHeld().
     * The room is shown as "Reserved" for as long as such a booking exists, and goes back to
     * "Available" by itself the day after the check-out date (no scheduler needed).
     */

    /** Active reservations for every room of one hotel (used to mark rooms on the rooms page). */
    @Query("select b from Booking b where b.room.hotel.id = :hotelId and b.status = 'APPROVED' " +
            "and b.roomReleased = false and b.checkOutDate >= :today")
    List<Booking> findActiveReservationsByHotel(@Param("hotelId") Long hotelId, @Param("today") LocalDate today);

    /*
     * Date-based availability: an APPROVED booking (whose room was not released early) blocks its room
     * for the nights [checkIn, checkOut). Two stays overlap when one starts before the other ends.
     */
    @Query("select b from Booking b where b.status = 'APPROVED' and b.roomReleased = false " +
            "and b.checkInDate < :checkOut and b.checkOutDate > :checkIn")
    List<Booking> findApprovedOverlapping(@Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut);

    @Query("select b from Booking b where b.room.id = :roomId and b.status = 'APPROVED' and b.roomReleased = false " +
            "and b.checkInDate < :checkOut and b.checkOutDate > :checkIn")
    List<Booking> findApprovedOverlappingForRoom(@Param("roomId") Long roomId,
                                                 @Param("checkIn") LocalDate checkIn,
                                                 @Param("checkOut") LocalDate checkOut);

    List<Booking> findByGroupReference(String groupReference);

    /** Active reservations for one room. */
    @Query("select b from Booking b where b.room.id = :roomId and b.status = 'APPROVED' " +
            "and b.roomReleased = false and b.checkOutDate >= :today")
    List<Booking> findActiveReservationsByRoom(@Param("roomId") Long roomId, @Param("today") LocalDate today);
}