package com.Reservation.Hotel.payment;

import com.Reservation.Hotel.model.Booking;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Demo hosted payment page ("AuraPay") using the redirect flow real gateways use (PayHere, Stripe Checkout):
 * <ol>
 *   <li>the hotel site creates a payment session and redirects the guest to the gateway page;</li>
 *   <li>the guest enters the card there and confirms a one-time code (3-D Secure style);</li>
 *   <li>the card is charged through {@link PaymentService} and the guest is redirected back to the hotel site,
 *       which reads the result from the session on the server.</li>
 * </ol>
 * Sessions are kept in memory and expire after {@link #SESSION_TTL}. No money moves.
 */
@Service
public class HostedCheckoutService {

    public static final Duration SESSION_TTL = Duration.ofMinutes(15);
    public static final int MAX_OTP_ATTEMPTS = 3;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);

    private final PaymentService paymentService;
    private final PaymentAttemptLimiter attemptLimiter;
    private final Map<String, HostedPaymentSession> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public HostedCheckoutService(PaymentService paymentService, PaymentAttemptLimiter attemptLimiter) {
        this.paymentService = paymentService;
        this.attemptLimiter = attemptLimiter;
    }

    /** Step 1 - the hotel site opens a payment session for the guest's payable bookings. */
    public HostedPaymentSession create(List<Booking> bookings, String username, Instant now) {
        purge(now);
        List<HostedPaymentSession.LineItem> items = bookings.stream()
                .map(b -> new HostedPaymentSession.LineItem(
                        b.getHotel().getName(),
                        b.getRoom().getRoomType() + " · " + b.getCheckInDate().format(DAY) + " – " + b.getCheckOutDate().format(DAY),
                        b.getAmountToPay()))
                .collect(Collectors.toList());
        String reference = bookings.stream().map(Booking::getDisplayReference).collect(Collectors.joining(", "));
        String id = UUID.randomUUID().toString().replace("-", "");
        HostedPaymentSession s = new HostedPaymentSession(id, username,
                bookings.stream().map(Booking::getId).collect(Collectors.toList()), items,
                PaymentService.total(bookings), reference, now, now.plus(SESSION_TTL));
        sessions.put(id, s);
        return s;
    }

    /** The guest's own session, or null. Expired sessions are closed on the way. */
    public HostedPaymentSession find(String id, String username, Instant now) {
        HostedPaymentSession s = id == null ? null : sessions.get(id);
        if (s == null || !s.getUsername().equalsIgnoreCase(username)) return null;
        synchronized (s) {
            if (!s.isFinished() && !now.isBefore(s.getExpiresAt())) {
                s.finish(HostedPaymentSession.Status.EXPIRED, null,
                        "The payment session timed out after " + SESSION_TTL.toMinutes() + " minutes. You have not been charged.");
            }
        }
        return s;
    }

    /** Step 2 - card details. Returns the problem (field + message) or null when the code step can start. */
    public CardDetails.Problem submitCard(HostedPaymentSession s, CardDetails card, Instant now) {
        synchronized (s) {
            if (s.getStatus() != HostedPaymentSession.Status.OPEN) return new CardDetails.Problem(null, "This payment is no longer open.");
            long wait = attemptLimiter.secondsLocked(s.getUsername(), now);
            if (wait > 0) {
                return new CardDetails.Problem(null, "Too many unsuccessful payment attempts. For your security, please try again in "
                        + ((wait + 59) / 60) + " minute" + (wait > 60 ? "s" : "") + ".");
            }
            CardDetails.Problem problem = card.problem(YearMonth.from(now.atZone(java.time.ZoneId.systemDefault())));
            if (problem != null) {
                attemptLimiter.recordFailure(s.getUsername(), now);
                return problem;
            }
            s.awaitOtp(card, String.format("%06d", random.nextInt(1_000_000)));
            return null;
        }
    }

    /**
     * Step 3 - the one-time code. A wrong code leaves the session waiting (until the attempts run out);
     * the right code charges the card and finishes the session.
     * @return an error to show on the code page, or null when the session is finished
     */
    public String confirmOtp(HostedPaymentSession s, String code, Instant now) {
        synchronized (s) {
            if (s.getStatus() != HostedPaymentSession.Status.AWAITING_OTP) return null;
            String typed = code == null ? "" : code.replaceAll("\\s", "");
            if (!s.otpMatches(typed)) {
                s.wrongOtp();
                if (s.getOtpAttemptsLeft() == 0) {
                    attemptLimiter.recordFailure(s.getUsername(), now);
                    s.finish(HostedPaymentSession.Status.FAILED, null,
                            "The verification code was wrong too many times. You have not been charged.");
                    return null;
                }
                return "That code is not right - " + s.getOtpAttemptsLeft() + (s.getOtpAttemptsLeft() == 1 ? " try" : " tries") + " left.";
            }
            CardDetails card = s.takeCard();
            List<Booking> bookings = paymentService.payableBookings(s.getBookingIds(), s.getUsername());
            if (bookings.size() != s.getBookingIds().size()) {
                s.finish(HostedPaymentSession.Status.FAILED, null, "These bookings no longer need payment. You have not been charged.");
                return null;
            }
            PaymentService.PaymentOutcome outcome = paymentService.pay(bookings, card, s.getUsername());
            if (outcome.success()) {
                s.finish(HostedPaymentSession.Status.PAID, outcome.payment().getTransactionId(), outcome.message());
            } else {
                s.finish(HostedPaymentSession.Status.FAILED, null, outcome.message());
            }
            return null;
        }
    }

    /** "Use a different card" from the code step. */
    public void changeCard(HostedPaymentSession s) {
        synchronized (s) {
            if (s.getStatus() == HostedPaymentSession.Status.AWAITING_OTP) s.backToCard(null);
        }
    }

    public void cancel(HostedPaymentSession s) {
        synchronized (s) {
            if (!s.isFinished()) s.finish(HostedPaymentSession.Status.CANCELLED, null, "Payment cancelled. You have not been charged.");
        }
    }

    public int attemptsLeft(String username, Instant now) {
        return attemptLimiter.attemptsLeft(username, now);
    }

    /** Forget sessions finished or expired more than an hour ago. */
    private void purge(Instant now) {
        sessions.values().removeIf(s -> s.getExpiresAt().plus(Duration.ofHours(1)).isBefore(now));
    }
}
