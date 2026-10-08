package com.Reservation.Hotel.repository;

import com.Reservation.Hotel.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByHotelIdAndHiddenFalseOrderByCreatedAtDesc(Long hotelId);

    List<Review> findByHotelIdOrderByCreatedAtDesc(Long hotelId);

    boolean existsByBookingId(Long bookingId);

    /** Latest well-rated visible reviews, for the home page. */
    List<Review> findTop3ByHiddenFalseAndRatingGreaterThanEqualAndCommentIsNotNullOrderByCreatedAtDesc(int minRating);

    List<Review> findByUsername(String username);

    /** [hotelId, average rating, review count] for every hotel that has visible reviews. */
    @Query("select r.hotel.id, avg(r.rating), count(r) from Review r where r.hidden = false group by r.hotel.id")
    List<Object[]> ratingSummary();

    @Query("select avg(r.rating) from Review r where r.hotel.id = :hotelId and r.hidden = false")
    Double averageForHotel(@Param("hotelId") Long hotelId);

    long countByHotelIdAndHiddenFalse(Long hotelId);
}
