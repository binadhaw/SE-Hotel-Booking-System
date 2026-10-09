package com.Reservation.Hotel.pricing;

import org.springframework.stereotype.Component;

import java.util.Optional;

/** Strategy: a coupon code the guest typed in, valid right now for the booking's hotel. */
@Component
public class CouponDiscountStrategy implements DiscountStrategy {

    private final PromotionService promotionService;

    public CouponDiscountStrategy(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @Override
    public Optional<AppliedDiscount> evaluate(Booking booking) {
        Long hotelId = booking.getHotel() != null ? booking.getHotel().getId() : null;
        Promotion coupon = promotionService.findValidCoupon(booking.getPromoCode(), hotelId);
        if (coupon == null || coupon.getDiscountPercentage() == null) return Optional.empty();
        double pct = coupon.getDiscountPercentage();
        return Optional.of(new AppliedDiscount(pct / 100.0, String.format("Coupon %s (%.0f%%)", coupon.getCode(), pct)));
    }
}
