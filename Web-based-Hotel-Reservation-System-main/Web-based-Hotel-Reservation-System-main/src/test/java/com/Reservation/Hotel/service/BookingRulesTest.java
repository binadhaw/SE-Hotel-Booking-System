package com.Reservation.Hotel.service;

import com.Reservation.Hotel.model.Hotel;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Unit tests for the pure business rules (no database, no Spring context). */
class BookingRulesTest {

    private static Room room(int sleeps) {
        Room r = new Room();
        r.setMaxGuests(sleeps);
        return r;
    }

    // ---------------- multi-room guest split (PBI-06) ----------------

    @Test
    void splitGuestsGivesEveryRoomAnAdultAndRespectsCapacity() {
        int[][] split = BookingService.splitGuests(List.of(room(2), room(3)), 3, 2);
        assertTrue(split[0][0] >= 1, "room 1 has an adult");
        assertTrue(split[1][0] >= 1, "room 2 has an adult");
        assertEquals(5, split[0][0] + split[0][1] + split[1][0] + split[1][1], "everyone is placed");
        assertTrue(split[0][0] + split[0][1] <= 2);
        assertTrue(split[1][0] + split[1][1] <= 3);
    }

    @Test
    void splitGuestsPutsOverflowInTheBiggestRoom() {
        int[][] split = BookingService.splitGuests(List.of(room(1), room(2)), 2, 3);
        assertEquals(5, split[0][0] + split[0][1] + split[1][0] + split[1][1]);
        assertEquals(1, split[0][0] + split[0][1], "the single room stays at capacity");
    }

    // ---------------- promotions ----------------

    private final PromotionService promotions = new PromotionService(
            mock(PromotionRepository.class), mock(UserService.class), mock(NotificationService.class), mock(EmailService.class));

    private static Promotion promo(Promotion.PromotionType type) {
        Promotion p = new Promotion();
        p.setType(type);
        p.setDiscountPercentage(10.0);
        p.setActive(true);
        p.setValidFrom(LocalDateTime.now().minusDays(1));
        return p;
    }

    @Test
    void inactiveOrExpiredPromotionIsNotActive() {
        Promotion p = promo(Promotion.PromotionType.DISCOUNT);
        assertTrue(promotions.isPromotionCurrentlyActive(p));
        p.setValidUntil(LocalDateTime.now().minusMinutes(1));
        assertFalse(promotions.isPromotionCurrentlyActive(p), "expired");
        p.setValidUntil(null);
        p.setActive(false);
        assertFalse(promotions.isPromotionCurrentlyActive(p), "switched off");
    }

    @Test
    void futurePromotionIsNotActiveYet() {
        Promotion p = promo(Promotion.PromotionType.DISCOUNT);
        p.setValidFrom(LocalDateTime.now().plusDays(2));
        assertFalse(promotions.isPromotionCurrentlyActive(p));
    }

    @Test
    void hotelPromotionOnlyAppliesToItsHotel() {
        Hotel h = new Hotel();
        h.setId(7L);
        Promotion p = promo(Promotion.PromotionType.COUPON);
        assertTrue(promotions.appliesToHotel(p, 99L), "platform-wide applies everywhere");
        p.setHotel(h);
        assertTrue(promotions.appliesToHotel(p, 7L));
        assertFalse(promotions.appliesToHotel(p, 99L));
        assertFalse(promotions.appliesToHotel(p, null));
    }

    // ---------------- reviews (PBI-08) ----------------

    private final ReviewService reviews = new ReviewService(mock(ReviewRepository.class), mock(org.springframework.context.ApplicationEventPublisher.class));

    @Test
    void onlyCompletedApprovedStaysCanBeReviewed() {
        Booking b = new Booking();
        b.setStatus("APPROVED");
        b.setCheckOutDate(LocalDate.now().plusDays(1));
        assertFalse(reviews.isStayCompleted(b), "stay not over yet");
        b.setCheckOutDate(LocalDate.now());
        assertTrue(reviews.isStayCompleted(b), "check-out day reached");
        b.setStatus("CANCELLED");
        assertFalse(reviews.isStayCompleted(b), "cancelled stays cannot be reviewed");
        b.setStatus("APPROVED");
        b.setCheckOutDate(LocalDate.now().plusDays(3));
        b.setRoomReleased(true);
        assertTrue(reviews.isStayCompleted(b), "guest checked out early");
    }
}
