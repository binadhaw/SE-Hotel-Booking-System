package com.Reservation.Hotel.dto;

/** One hotel in the search results plus the numbers the card shows. */
public class HotelSearchResult {

    private final Hotel hotel;
    private final Double fromPrice;      // cheapest matching room per night
    private final int matchingRooms;     // rooms that pass price / availability filters
    private final int sleeps;            // total recommended guests of those rooms
    private double averageRating;
    private long reviewCount;

    public HotelSearchResult(Hotel hotel, Double fromPrice, int matchingRooms, int sleeps) {
        this.hotel = hotel;
        this.fromPrice = fromPrice;
        this.matchingRooms = matchingRooms;
        this.sleeps = sleeps;
    }

    public Hotel getHotel() { return hotel; }
    public Double getFromPrice() { return fromPrice; }
    public int getMatchingRooms() { return matchingRooms; }
    public int getSleeps() { return sleeps; }

    public double getAverageRating() { return averageRating; }
    public void setAverageRating(double averageRating) { this.averageRating = averageRating; }

    public long getReviewCount() { return reviewCount; }
    public void setReviewCount(long reviewCount) { this.reviewCount = reviewCount; }
}
