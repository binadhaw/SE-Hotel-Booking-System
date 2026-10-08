package com.Reservation.Hotel.events;

/** Observer pattern: published when a review is posted, replied to, hidden or restored. */
public record ReviewEvent(Review review, Type type, String actor) {

    public enum Type { POSTED, REPLIED, HIDDEN, RESTORED }
}
