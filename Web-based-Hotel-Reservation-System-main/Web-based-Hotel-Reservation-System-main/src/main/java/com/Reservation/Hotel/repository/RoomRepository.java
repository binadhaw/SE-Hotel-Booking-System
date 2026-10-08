package com.Reservation.Hotel.repository;

import com.Reservation.Hotel.model.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByHotelId(Long hotelId);

    /**
     * SELECT ... FOR UPDATE on the given rooms, held until the surrounding transaction ends.
     * Ordered by id so two transactions locking overlapping sets of rooms cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id in :ids order by r.id")
    List<Room> lockAllById(@Param("ids") Collection<Long> ids);
}
