package com.Reservation.Hotel.pricing;

import com.Reservation.Hotel.model.Booking;

import java.util.Optional;

/**
 * DESIGN PATTERN - Strategy.
 *
 * One way of giving a guest a discount. Each discount source (a typed-in coupon, a standard discount
 * picked from the list, ...) is its own strategy class; {@link PricingService} asks every strategy and
 * applies the single best offer. A new kind of discount (e.g. a long-stay discount) is added by writing
 * one new {@code @Component} that implements this interface - the booking code does not change.
 */
public interface DiscountStrategy {

    /**
     * @return the discount this strategy gives for the booking, or empty when it does not apply
     */
    Optional<AppliedDiscount> evaluate(Booking booking);
}
