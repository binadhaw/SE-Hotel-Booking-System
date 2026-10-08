package com.Reservation.Hotel.pricing;

import org.springframework.stereotype.Component;

import java.util.Optional;

/** Strategy: a standard (seasonal) discount the guest chose from the list on the booking form. */
@Component
public class StandardDiscountStrategy implements DiscountStrategy {

    private final PromotionService promotionService;

    public StandardDiscountStrategy(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @Override
    public Optional<AppliedDiscount> evaluate(Booking booking) {
        if (booking.getDiscountId() == null) return Optional.empty();
        Long hotelId = booking.getHotel() != null ? booking.getHotel().getId() : null;
        return promotionService.getPromotionById(booking.getDiscountId())
                .filter(p -> p.getType() == Promotion.PromotionType.DISCOUNT)
                .filter(p -> p.getDiscountPercentage() != null)
                .filter(promotionService::isPromotionCurrentlyActive)
                .filter(p -> promotionService.appliesToHotel(p, hotelId))
                .map(p -> new AppliedDiscount(p.getDiscountPercentage() / 100.0,
                        String.format("%s (%.0f%%)", p.getDescription() == null ? "Standard discount" : p.getDescription(),
                                p.getDiscountPercentage())));
    }
}
