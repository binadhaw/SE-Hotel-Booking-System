package com.Reservation.Hotel;

import com.Reservation.Hotel.events.BookingEvent;
import com.Reservation.Hotel.pricing.AppliedDiscount;
import com.Reservation.Hotel.pricing.DiscountStrategy;
import com.Reservation.Hotel.pricing.PricingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Shows that each design pattern used in the project actually works as described in docs/DESIGN_PATTERNS.md. */
@SpringBootTest
@Transactional
class DesignPatternsTest {

    @Autowired PricingService pricingService;
    @Autowired List<DiscountStrategy> strategies;
    @Autowired PromotionRepository promotionRepository;
    @Autowired HotelRepository hotelRepository;
    @Autowired ApplicationEventPublisher events;
    @Autowired ActivityLogRepository activityLogRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired HotelImageStorageService hotelImages;
    @Autowired RoomImageStorageService roomImages;

    private Hotel hotel(String manager) {
        Hotel h = new Hotel("Pattern Hotel", "Galle", Hotel.APPROVED, "test", "");
        h.setManagerUsername(manager);
        return hotelRepository.save(h);
    }

    private Promotion promotion(Promotion.PromotionType type, String code, double pct) {
        Promotion p = new Promotion();
        p.setType(type);
        p.setCode(code);
        p.setDiscountPercentage(pct);
        p.setActive(true);
        p.setValidFrom(LocalDateTime.now().minusDays(1));
        return promotionRepository.save(p);
    }

    // ---------------- Strategy ----------------

    @Test
    void strategyEveryDiscountSourceIsAPluggableBean() {
        assertTrue(strategies.size() >= 2, "coupon + standard discount strategies are registered");
    }

    @Test
    void strategyPricingPicksTheSingleBestDiscount() {
        Hotel h = hotel("manager");
        promotion(Promotion.PromotionType.COUPON, "PATTERN15", 15);
        Promotion standard = promotion(Promotion.PromotionType.DISCOUNT, null, 25);

        Booking b = new Booking();
        b.setHotel(h);
        b.setPromoCode("pattern15");
        AppliedDiscount couponOnly = pricingService.bestDiscount(b);
        assertEquals(0.15, couponOnly.rate(), 1e-9);
        assertTrue(couponOnly.label().contains("PATTERN15"));

        b.setDiscountId(standard.getId());
        assertEquals(0.25, pricingService.bestDiscount(b).rate(), 1e-9, "the bigger offer wins, offers are not stacked");

        assertEquals(AppliedDiscount.NONE, pricingService.bestDiscount(new Booking()), "no discount sources -> none");
    }

    @Test
    void strategyANewDiscountTypeNeedsNoChangesToExistingCode() {
        DiscountStrategy longStay = booking -> Optional.of(new AppliedDiscount(0.30, "Long stay (30%)"));
        PricingService withExtra = new PricingService(List.of(longStay));
        assertEquals(0.30, withExtra.bestDiscount(new Booking()).rate(), 1e-9);
    }

    // ---------------- Observer ----------------

    @Test
    void observerOneEventReachesEveryListener() {
        Hotel h = hotel("manager");
        Room room = new Room("9", "Suite", 100.0, true, h);
        Booking b = new Booking();
        b.setHotel(h);
        b.setRoom(room);
        b.setUsername("user");
        b.setReference("BK-PATTERN1");
        b.setCheckInDate(LocalDate.now().plusDays(3));
        b.setCheckOutDate(LocalDate.now().plusDays(5));

        long notificationsBefore = notificationRepository.count();
        events.publishEvent(new BookingEvent(b, BookingEvent.Type.CREATED, "user", null));

        // AuditLogListener wrote the activity log ...
        ActivityLog last = activityLogRepository.findTop15ByOrderByCreatedAtDesc().get(0);
        assertEquals("BOOKING_CREATED", last.getAction());
        assertTrue(last.getDetails().contains("BK-PATTERN1"));
        // ... and NotificationListener alerted the guest and the hotel manager
        assertEquals(notificationsBefore + 2, notificationRepository.count());
        assertTrue(notificationRepository.findTop5ByUsernameOrderByCreatedAtDesc("manager").get(0)
                .getTitle().startsWith("New booking request"));
    }

    // ---------------- Template Method ----------------

    @Test
    void templateMethodSharedAlgorithmDifferentHooks() {
        ImageStorageService[] services = {hotelImages, roomImages};
        for (ImageStorageService s : services) {
            var ok = new MockMultipartFile("images", "a.png", "image/png", new byte[]{1});
            var html = new MockMultipartFile("images", "evil.html", "text/html", new byte[]{1});
            assertNull(s.validate(List.of(ok), 0));
            assertNotNull(s.validate(List.of(html), 0), "same type check for every subclass");
        }
        // hooks differ per subclass
        assertEquals(8, hotelImages.maxImages());
        assertEquals(6, roomImages.maxImages());
        assertTrue(hotelImages.validate(List.of(), 9).contains("hotel"));
        assertTrue(roomImages.validate(List.of(), 7).contains("room"));
    }
}
