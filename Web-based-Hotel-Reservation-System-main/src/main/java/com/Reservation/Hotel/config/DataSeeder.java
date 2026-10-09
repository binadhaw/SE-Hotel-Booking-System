package com.Reservation.Hotel.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates the demo accounts on first start (they used to be hard-coded in-memory users).
 * Existing accounts are never overwritten.
 */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private final UserService userService;
    private final HotelService hotelService;
    private final PromotionService promotionService;
    private final AttractionService attractionService;

    public DataSeeder(UserService userService, HotelService hotelService, PromotionService promotionService,
                      AttractionService attractionService) {
        this.attractionService = attractionService;
        this.userService = userService;
        this.hotelService = hotelService;
        this.promotionService = promotionService;
    }

    @Override
    public void run(String... args) {
        userService.createIfMissing("admin", "admin123", "System Administrator", "admin@hotel.local", AppUser.ROLE_ADMIN);
        userService.createIfMissing("manager", "manager123", "Hotel Manager", "manager@hotel.local", AppUser.ROLE_MANAGER);
        userService.createIfMissing("agent", "agent123", "Travel Agent", "agent@hotel.local", AppUser.ROLE_AGENT);
        userService.createIfMissing("user", "user123", "Demo Tourist", "user@hotel.local", AppUser.ROLE_USER);

        // Hotels registered before hotel ownership existed belong to the demo manager
        hotelService.assignUnownedHotels("manager");

        // The two demo coupons that used to be hard-coded in BookingService
        promotionService.seedCouponIfMissing("SAVE10", 10, "10% off any stay");
        promotionService.seedCouponIfMissing("WELCOME20", 20, "Welcome offer - 20% off your first booking");

        // Well-known Sri Lankan attractions, shown on hotel pages by town
        attractionService.seedIfEmpty();
    }
}
