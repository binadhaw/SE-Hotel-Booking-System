package com.Reservation.Hotel.dto;

/** Average rating and number of visible reviews for one hotel. */
public class RatingStats {

    private final double averageRating;
    private final long reviewCount;

    public RatingStats(double averageRating, long reviewCount) {
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
    }

    public double getAverageRating() { return averageRating; }
    public long getReviewCount() { return reviewCount; }
}
