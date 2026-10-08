package com.Reservation.Hotel.pricing;

import com.Reservation.Hotel.model.Booking;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Context of the Strategy pattern: Spring injects every {@link DiscountStrategy} bean, and the guest gets
 * the single best one. Offers are never stacked, so a coupon and a standard discount cannot be combined.
 */
@Service
public class PricingService {

    private final List<DiscountStrategy> strategies;

    public PricingService(List<DiscountStrategy> strategies) {
        this.strategies = strategies;
    }

    /** The best discount available for this booking, or {@link AppliedDiscount#NONE}. */
    public AppliedDiscount bestDiscount(Booking booking) {
        return strategies.stream()
                .map(s -> s.evaluate(booking))
                .flatMap(Optional::stream)
                .max(Comparator.comparingDouble(AppliedDiscount::rate))
                .orElse(AppliedDiscount.NONE);
    }
}
