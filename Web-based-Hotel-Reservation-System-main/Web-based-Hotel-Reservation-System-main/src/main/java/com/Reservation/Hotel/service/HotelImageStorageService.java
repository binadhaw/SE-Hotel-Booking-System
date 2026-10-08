package com.Reservation.Hotel.service;

import org.springframework.stereotype.Service;

/**
 * Hotel photos under ./uploads/hotels/ (served publicly by WebConfig).
 * Template Method: only the hooks are defined here - the algorithm is in {@link ImageStorageService}.
 */
@Service
public class HotelImageStorageService extends ImageStorageService {

    public static final int MAX_IMAGES = 8;

    @Override
    protected String folder() {
        return "hotels";
    }

    @Override
    public int maxImages() {
        return MAX_IMAGES;
    }

    @Override
    protected String itemName() {
        return "hotel";
    }
}
