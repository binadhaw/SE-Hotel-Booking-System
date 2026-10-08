package com.Reservation.Hotel.service;

import com.Reservation.Hotel.dto.RatingStats;
import com.Reservation.Hotel.repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** Average guest rating per hotel (visible reviews only), used by search sorting and hotel cards. */
@Service
public class RatingProvider {

    private final ReviewRepository reviewRepository;

    public RatingProvider(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public double averageRating(Long hotelId) {
        Double avg = reviewRepository.averageForHotel(hotelId);
        return avg == null ? 0.0 : avg;
    }

    public long reviewCount(Long hotelId) {
        return reviewRepository.countByHotelIdAndHiddenFalse(hotelId);
    }

    /**
     * Ratings of every reviewed hotel in one query (hotels without reviews are absent) - search uses this
     * instead of two queries per hotel, so it stays fast as the number of hotels grows.
     */
    public Map<Long, RatingStats> allHotelRatings() {
        Map<Long, RatingStats> ratings = new HashMap<>();
        for (Object[] row : reviewRepository.ratingSummary()) {
            ratings.put((Long) row[0], new RatingStats(((Number) row[1]).doubleValue(), ((Number) row[2]).longValue()));
        }
        return ratings;
    }
}
