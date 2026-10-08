package com.Reservation.Hotel.events;

import java.util.List;

/**
 * DESIGN PATTERN - Observer (the "subject" side).
 *
 * Published whenever a booking changes state. The code that changes the booking only announces
 * what happened; every interested party (guest notifications, manager alerts, receipt email,
 * audit log) is a separate listener in {@code com.Reservation.Hotel.events.listeners}.
 *
 * @param bookings the booking(s) concerned - several for a multi-room group booking
 * @param type     what happened
 * @param actor    username of who did it
 * @param note     reason / extra detail (rejection reason, cancellation note...), may be null
 */
public record BookingEvent(List<Booking> bookings, Type type, String actor, String note) {

    public enum Type { CREATED, APPROVED, PAID_ONLINE, REJECTED, CANCELLED_BY_GUEST, CANCELLED_BY_HOTEL, REFUND_COMPLETED }

    public BookingEvent(Booking booking, Type type, String actor, String note) {
        this(List.of(booking), type, actor, note);
    }

    public Booking first() {
        return bookings.get(0);
    }
}
