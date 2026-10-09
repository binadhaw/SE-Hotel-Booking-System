package com.Reservation.Hotel.service;

import org.springframework.stereotype.Service;

/**
 * Room photos under ./uploads/rooms/ (served publicly by WebConfig).
 * Template Method: only the hooks are defined here - the algorithm is in {@link ImageStorageService}.
 */
@Service
public class RoomImageStorageService extends ImageStorageService {

    public static final int MAX_IMAGES = 6;

    @Override
    protected String folder() {
        return "rooms";
    }

    @Override
    public int maxImages() {
        return MAX_IMAGES;
    }

    @Override
    protected String itemName() {
        return "room";
    }
}
