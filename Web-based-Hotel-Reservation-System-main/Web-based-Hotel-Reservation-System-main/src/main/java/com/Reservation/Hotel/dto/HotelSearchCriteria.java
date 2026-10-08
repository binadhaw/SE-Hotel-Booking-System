package com.Reservation.Hotel.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Filters from the hotel search form (PBI-01 price, PBI-05 facilities, PBI-06 dates / party size). */
public class HotelSearchCriteria {

    private String keyword;
    private Double minPrice;
    private Double maxPrice;
    private List<String> amenities = new ArrayList<>();

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkIn;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate checkOut;

    private Integer guests;

    // recommended | price_asc | price_desc | rating | name
    private String sort = "recommended";

    public boolean hasDates() {
        return checkIn != null && checkOut != null && checkOut.isAfter(checkIn);
    }

    public boolean isFiltered() {
        return (keyword != null && !keyword.isBlank()) || minPrice != null || maxPrice != null
                || (amenities != null && !amenities.isEmpty()) || hasDates() || guests != null;
    }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public Double getMinPrice() { return minPrice; }
    public void setMinPrice(Double minPrice) { this.minPrice = minPrice; }

    public Double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(Double maxPrice) { this.maxPrice = maxPrice; }

    public List<String> getAmenities() { return amenities; }
    public void setAmenities(List<String> amenities) { this.amenities = amenities == null ? new ArrayList<>() : amenities; }

    public LocalDate getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDate checkIn) { this.checkIn = checkIn; }

    public LocalDate getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDate checkOut) { this.checkOut = checkOut; }

    public Integer getGuests() { return guests; }
    public void setGuests(Integer guests) { this.guests = guests; }

    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
}
