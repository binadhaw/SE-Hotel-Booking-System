package com.Reservation.Hotel.repository;

import com.Reservation.Hotel.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUsernameOrderByCreatedAtDesc(String username);

    List<Notification> findTop5ByUsernameOrderByCreatedAtDesc(String username);

    long countByUsernameAndReadFlagFalse(String username);

    void deleteByUsernameAndReadFlagTrue(String username);

    @Modifying
    @Query("update Notification n set n.readFlag = true where n.username = :username and n.readFlag = false")
    int markAllRead(@Param("username") String username);
}
