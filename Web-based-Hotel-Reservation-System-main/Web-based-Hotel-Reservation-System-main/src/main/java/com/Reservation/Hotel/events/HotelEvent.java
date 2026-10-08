package com.Reservation.Hotel.events;

import com.Reservation.Hotel.model.Hotel;

/**
 * Observer pattern: published when a hotel is registered, approved, rejected or removed.
 *
 * @param note rejection / removal reason or a summary, may be null
 */
public record HotelEvent(Hotel hotel, Type type, String actor, String note) {

    public enum Type { REGISTERED, APPROVED, REJECTED, REMOVED }
}
