package com.Reservation.Hotel.events.listeners;

import com.Reservation.Hotel.events.BookingEvent;
import com.Reservation.Hotel.events.HotelEvent;
import com.Reservation.Hotel.events.ReviewEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Observer: writes every domain event to the admin activity log (PBI-14).
 * No feature code has to remember to log - it only publishes the event.
 */
@Component
public class AuditLogListener {

    private final ActivityLogService activityLog;

    public AuditLogListener(ActivityLogService activityLog) {
        this.activityLog = activityLog;
    }

    @EventListener
    public void onBooking(BookingEvent e) {
        Booking b = e.first();
        String refs = e.bookings().stream().map(Booking::getDisplayReference).collect(Collectors.joining(", "));
        String details = refs + " at " + b.getHotel().getName()
                + (e.type() == BookingEvent.Type.CREATED ? " (" + b.getCheckInDate() + " to " + b.getCheckOutDate() + ")" : "")
                + (e.note() != null ? ": " + e.note() : "");
        activityLog.log(e.actor(), "BOOKING_" + e.type().name(), details);
    }

    @EventListener
    public void onHotel(HotelEvent e) {
        activityLog.log(e.actor(), "HOTEL_" + e.type().name(),
                e.hotel().getName() + " (" + e.hotel().getLocation() + ")" + (e.note() != null ? ": " + e.note() : ""));
    }

    @EventListener
    public void onReview(ReviewEvent e) {
        activityLog.log(e.actor(), "REVIEW_" + e.type().name(),
                e.review().getRating() + "-star review of " + e.review().getHotel().getName());
    }
}
