package com.Reservation.Hotel.pricing;

/**
 * Result of a {@link DiscountStrategy}: the rate (0.10 = 10%) and a label shown to the guest,
 * e.g. "Coupon SAVE10 (10%)".
 */
public record AppliedDiscount(double rate, String label) {

    public static final AppliedDiscount NONE = new AppliedDiscount(0.0, null);

    public AppliedDiscount {
        if (rate < 0 || rate > 1) throw new IllegalArgumentException("Discount rate must be between 0 and 1");
    }
}
