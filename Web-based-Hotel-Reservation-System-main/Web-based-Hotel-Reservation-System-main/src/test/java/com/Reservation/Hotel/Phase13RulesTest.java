package com.Reservation.Hotel;

import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.payment.CardDetails;
import com.Reservation.Hotel.payment.SimulatedPaymentGateway;
import com.Reservation.Hotel.security.FieldEncryptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for Phase 13: card checks, the demo gateway, field encryption and offer targeting. */
class Phase13RulesTest {

    private static final YearMonth NOW = YearMonth.of(2026, 10);

    // ---------------- card validation ----------------

    @Test
    void validCardsPass() {
        assertNull(new CardDetails("Jane Doe", "4242 4242 4242 4242", "12/30", "123").validate(NOW));
        assertNull(new CardDetails("Jane Doe", "5555-5555-5555-4444", "01/27", "999").validate(NOW));
        assertNull(new CardDetails("Jane Doe", "378282246310005", "10/26", "1234").validate(NOW), "Amex, expires this month");
    }

    @Test
    void cardProblemsAreReported() {
        assertTrue(new CardDetails("Jane Doe", "4242424242424241", "12/30", "123").validate(NOW).contains("not valid"), "Luhn");
        assertTrue(new CardDetails("Jane Doe", "6011111111111117", "12/30", "123").validate(NOW).contains("We accept"), "Discover not accepted");
        assertTrue(new CardDetails("Jane Doe", "4242424242424242", "09/26", "123").validate(NOW).contains("expired"));
        assertTrue(new CardDetails("Jane Doe", "4242424242424242", "13/30", "123").validate(NOW).contains("MM/YY"));
        assertTrue(new CardDetails("Jane Doe", "378282246310005", "12/30", "123").validate(NOW).contains("4-digit"), "Amex needs 4");
        assertTrue(new CardDetails("J", "4242424242424242", "12/30", "123").validate(NOW).contains("name"));
    }

    @Test
    void brandsAndMasking() {
        CardDetails mc2 = new CardDetails("A B", "2223003122003222", "12/30", "123");
        assertEquals("Mastercard", mc2.brand(), "2-series Mastercard");
        assertEquals("3222", mc2.last4());
        assertFalse(mc2.toString().contains("2223003122003222"), "full number never printed");
    }

    @Test
    void demoGatewayOutcomes() {
        SimulatedPaymentGateway gw = new SimulatedPaymentGateway();
        assertTrue(gw.charge(new CardDetails("A B", "4242424242424242", "12/30", "123"), 100, "USD", "t").approved());
        assertFalse(gw.charge(new CardDetails("A B", "4000000000000002", "12/30", "123"), 100, "USD", "t").approved());
        assertFalse(gw.charge(new CardDetails("A B", "4000000000009995", "12/30", "123"), 100, "USD", "t").approved());
    }

    // ---------------- encryption ----------------

    @BeforeAll
    static void key() {
        FieldEncryptor.init("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");
    }

    @Test
    void encryptionRoundTripWithRandomIv() {
        String a = FieldEncryptor.encrypt("+94 77 123 4567");
        String b = FieldEncryptor.encrypt("+94 77 123 4567");
        assertTrue(a.startsWith(FieldEncryptor.PREFIX));
        assertNotEquals(a, b, "same value encrypts differently each time");
        assertEquals("+94 77 123 4567", FieldEncryptor.decrypt(a));
        assertEquals("legacy plain", FieldEncryptor.decrypt("legacy plain"), "old plain-text rows still readable");
        assertNull(FieldEncryptor.encrypt(null));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String enc = FieldEncryptor.encrypt("secret");
        char[] c = enc.toCharArray();
        int i = c.length - 5;
        c[i] = c[i] == 'A' ? 'B' : 'A';
        assertThrows(IllegalStateException.class, () -> FieldEncryptor.decrypt(new String(c)));
    }

    // ---------------- offer targeting ----------------

    private static Promotion hotelOffer(String town, double price, double pct) {
        Hotel h = new Hotel("H", town, Hotel.APPROVED, "", "");
        Room r = new Room("1", "Double", price, true, h);
        h.setRooms(List.of(r));
        Promotion p = new Promotion();
        p.setHotel(h);
        p.setDiscountPercentage(pct);
        return p;
    }

    @Test
    void offersAreTargetedByPreferences() {
        AppUser galle = new AppUser();
        galle.setPreferredDestination("Galle");
        AppUser budget = new AppUser();
        budget.setBudgetPerNight(100.0);
        AppUser none = new AppUser();

        Promotion galleOffer = hotelOffer("Galle Fort", 200, 20);   // $160 after discount
        assertTrue(PromotionService.isRelevant(galleOffer, galle));
        assertFalse(PromotionService.isRelevant(hotelOffer("Kandy", 200, 20), galle));
        assertFalse(PromotionService.isRelevant(galleOffer, budget), "$160 is over the $100 budget");
        assertTrue(PromotionService.isRelevant(hotelOffer("Ella", 110, 20), budget), "$88 fits");
        assertTrue(PromotionService.isRelevant(galleOffer, none), "no preferences -> every offer");
        assertTrue(PromotionService.isRelevant(new Promotion(), galle), "platform-wide offers reach everyone");
    }
}
