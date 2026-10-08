package com.Reservation.Hotel;

import com.Reservation.Hotel.payment.CardDetails;
import com.Reservation.Hotel.payment.PaymentAttemptLimiter;
import com.Reservation.Hotel.payment.SimulatedPaymentGateway;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

/** Advanced checkout rules: field-level card checks, brand lengths, attempt limiting and gateway outcomes. */
class CheckoutValidationTest {

    private static final YearMonth NOW = YearMonth.of(2026, 10);

    private static CardDetails.Problem problem(String holder, String number, String expiry, String cvv) {
        return new CardDetails(holder, number, expiry, cvv).problem(NOW);
    }

    @Test
    void eachProblemPointsAtItsField() {
        assertEquals("cardHolder", problem("", "4242424242424242", "12/30", "123").field());
        assertEquals("cardNumber", problem("Jane Doe", "", "12/30", "123").field());
        assertEquals("expiry", problem("Jane Doe", "4242424242424242", "", "123").field());
        assertEquals("cvv", problem("Jane Doe", "4242424242424242", "12/30", "12").field());
        assertNull(problem("Jane Doe", "4242 4242 4242 4242", "12/30", "123"));
    }

    @Test
    void nameRules() {
        assertTrue(problem("Jane D0e", "4242424242424242", "12/30", "123").message().contains("numbers"));
        assertTrue(problem("Jane  Doe", "4242424242424242", "12/30", "123").message().contains("extra spaces"));
        assertTrue(problem("--", "4242424242424242", "12/30", "123").message().contains("name"));
        assertNull(problem("Ánna-Marie O'Neil", "4242424242424242", "12/30", "123"), "accents, hyphens and apostrophes allowed");
    }

    @Test
    void numberRules() {
        assertTrue(problem("Jane Doe", "4242abcd42424242", "12/30", "123").message().contains("only contain digits"));
        assertTrue(problem("Jane Doe", "424242424242", "12/30", "123").message().contains("16 digits"), "Visa too short");
        assertTrue(problem("Jane Doe", "555555555555444", "12/30", "123").message().contains("16 digits"), "Mastercard too short");
        assertTrue(problem("Jane Doe", "3782822463100055", "12/30", "1234").message().contains("15 digits"), "Amex too long");
        assertTrue(problem("Jane Doe", "4444444444444444", "12/30", "123").message().contains("not valid"), "repeated digits");
        assertTrue(problem("Jane Doe", "9999888877776666", "12/30", "123").message().contains("We accept"));
    }

    @Test
    void expiryRules() {
        assertNull(problem("Jane Doe", "4242424242424242", "12/2030", "123"), "MM/YYYY accepted");
        assertTrue(problem("Jane Doe", "4242424242424242", "00/30", "123").message().contains("MM/YY"));
        assertTrue(problem("Jane Doe", "4242424242424242", "12/60", "123").message().contains("too far"));
        assertNull(problem("Jane Doe", "4242424242424242", "10/26", "123"), "expires this month - still valid");
    }

    @Test
    void cvvMessageExplainsWhereToLook() {
        assertTrue(problem("Jane Doe", "378282246310005", "12/30", "123").message().contains("front"), "Amex");
        assertTrue(problem("Jane Doe", "4242424242424242", "12/30", "1234").message().contains("back"), "Visa");
    }

    @Test
    void repeatedFailuresLockTheCheckoutForAWhile() {
        PaymentAttemptLimiter limiter = new PaymentAttemptLimiter();
        Instant t = Instant.parse("2026-10-06T10:00:00Z");
        for (int i = 0; i < PaymentAttemptLimiter.MAX_FAILURES - 1; i++) limiter.recordFailure("Jane", t.plusSeconds(i));
        assertEquals(0, limiter.secondsLocked("jane", t.plusSeconds(10)), "four failures - still allowed");
        assertEquals(1, limiter.attemptsLeft("jane", t.plusSeconds(10)));

        limiter.recordFailure("jane", t.plusSeconds(20));
        assertTrue(limiter.secondsLocked("JANE", t.plusSeconds(30)) > 0, "fifth failure locks (case-insensitive user)");
        assertEquals(0, limiter.secondsLocked("someone-else", t.plusSeconds(30)), "other users unaffected");

        assertEquals(0, limiter.secondsLocked("jane", t.plus(PaymentAttemptLimiter.WINDOW).plus(Duration.ofMinutes(1))),
                "old failures expire after the window");

        limiter.recordFailure("bob", t);
        limiter.reset("bob");
        assertEquals(PaymentAttemptLimiter.MAX_FAILURES, limiter.attemptsLeft("bob", t), "success clears the counter");
    }

    @Test
    void hostedPaymentSessionsExpireAndStayWithTheirOwner() {
        var hotel = new com.Reservation.Hotel.model.Hotel("Expiry Inn", "Galle", "APPROVED", "t", "Free WiFi");
        var room = new com.Reservation.Hotel.model.Room("E1", "Deluxe", 100.0, true, hotel);
        var b = new com.Reservation.Hotel.model.Booking();
        b.setId(1L);
        b.setHotel(hotel);
        b.setRoom(room);
        b.setCheckInDate(java.time.LocalDate.of(2026, 11, 1));
        b.setCheckOutDate(java.time.LocalDate.of(2026, 11, 3));
        b.setFinalAmount(200.0);
        var service = new com.Reservation.Hotel.payment.HostedCheckoutService(null, new PaymentAttemptLimiter());
        Instant t = Instant.parse("2026-10-06T10:00:00Z");

        var s = service.create(java.util.List.of(b), "jane", t);
        assertEquals(200.0, s.getAmount());
        assertNull(service.find(s.getId(), "someone-else", t), "another user cannot open it");
        assertEquals(com.Reservation.Hotel.payment.HostedPaymentSession.Status.OPEN, service.find(s.getId(), "JANE", t.plusSeconds(60)).getStatus());

        var later = service.find(s.getId(), "jane", t.plus(com.Reservation.Hotel.payment.HostedCheckoutService.SESSION_TTL));
        assertEquals(com.Reservation.Hotel.payment.HostedPaymentSession.Status.EXPIRED, later.getStatus());
        assertTrue(later.getMessage().contains("not been charged"));
        assertNotNull(service.submitCard(later, new CardDetails("Jane Doe", "4242424242424242", "12/30", "123"), t.plusSeconds(1000)),
                "an expired session refuses card details");
    }

    @Test
    void extraGatewayTestCards() {
        SimulatedPaymentGateway gw = new SimulatedPaymentGateway();
        CardDetails wrongCvv = new CardDetails("A B", "4000000000000127", "12/30", "123");
        CardDetails expired = new CardDetails("A B", "4000000000000069", "12/30", "123");
        CardDetails ok = new CardDetails("A B", "4242424242424242", "12/30", "123");
        assertTrue(gw.charge(wrongCvv, 100, "USD", "t").declineReason().contains("security code"));
        assertTrue(gw.charge(expired, 100, "USD", "t").declineReason().contains("expired"));
        assertFalse(gw.charge(ok, 60_000, "USD", "t").approved(), "over the card limit");
        assertNull(wrongCvv.validate(NOW), "these are valid card numbers - only the bank declines them");
    }
}
