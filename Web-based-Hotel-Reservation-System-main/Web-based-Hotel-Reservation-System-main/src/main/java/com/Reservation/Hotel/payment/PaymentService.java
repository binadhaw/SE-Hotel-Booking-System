package com.Reservation.Hotel.payment;

import com.Reservation.Hotel.events.BookingEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Online card payment for bookings (proposal: "book rooms with online payment ... the system confirms
 * bookings instantly"). A successful payment confirms the booking(s) straight away - no manager approval
 * is needed because the money has been received.
 */
@Service
public class PaymentService {

    /** Outcome shown to the guest. */
    public record PaymentOutcome(boolean success, String message, Payment payment) {}

    private final PaymentGateway gateway;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final ApplicationEventPublisher events;
    private final PaymentAttemptLimiter attemptLimiter;

    public PaymentService(PaymentGateway gateway, PaymentRepository paymentRepository, BookingRepository bookingRepository,
                          BookingService bookingService, ApplicationEventPublisher events, PaymentAttemptLimiter attemptLimiter) {
        this.gateway = gateway;
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.events = events;
        this.attemptLimiter = attemptLimiter;
    }

    /** Failed attempts left before the checkout is locked for a while. */
    public int attemptsLeft(String username) {
        return attemptLimiter.attemptsLeft(username, Instant.now());
    }

    /** Can this guest pay this booking online right now? */
    public boolean isPayable(Booking b, String username) {
        return b != null && username.equals(b.getUsername())
                && "PENDING".equals(b.getStatus())
                && b.getAmountToPay() > 0.009
                && b.getCheckInDate() != null && !b.getCheckInDate().isBefore(LocalDate.now());
    }

    /** The guest's own payable bookings among the given ids (ids that are not payable are dropped). */
    public List<Booking> payableBookings(List<Long> ids, String username) {
        if (ids == null) return List.of();
        return bookingRepository.findAllById(ids).stream()
                .filter(b -> isPayable(b, username))
                .collect(Collectors.toList());
    }

    public static double total(List<Booking> bookings) {
        return Math.round(bookings.stream().mapToDouble(Booking::getAmountToPay).sum() * 100.0) / 100.0;
    }

    public Payment findByTransactionId(String txn) {
        return paymentRepository.findByTransactionId(txn).orElse(null);
    }

    /**
     * Validates the card, makes sure every room is still free, charges the card through the gateway and
     * confirms the bookings. Declined attempts are recorded too (for support), but nothing is confirmed.
     */
    @Transactional
    public PaymentOutcome pay(List<Booking> bookings, CardDetails card, String username) {
        if (bookings.isEmpty()) return new PaymentOutcome(false, "There is nothing to pay for.", null);

        long wait = attemptLimiter.secondsLocked(username, Instant.now());
        if (wait > 0) {
            return new PaymentOutcome(false, "Too many unsuccessful payment attempts. For your security, please try again in "
                    + ((wait + 59) / 60) + " minute" + (wait > 60 ? "s" : "") + ".", null);
        }

        String cardProblem = card.validate(YearMonth.now());
        if (cardProblem != null) {
            attemptLimiter.recordFailure(username, Instant.now());
            return new PaymentOutcome(false, cardProblem, null);
        }

        // Don't take money for a room someone else has secured meanwhile. The room lock (held until this
        // transaction commits) makes a simultaneous payment for the same room wait, then see this one as a clash.
        bookingService.lockRooms(bookings);
        for (Booking b : bookings) {
            Booking clash = bookingService.findConflictingReservation(b);
            if (clash != null) {
                return new PaymentOutcome(false, "Room " + b.getRoom().getRoomNumber() + " was just booked by another guest for those dates. "
                        + "You have not been charged - please edit your booking or choose another room.", null);
            }
        }

        double amount = total(bookings);
        String refs = bookings.stream().map(Booking::getDisplayReference).collect(Collectors.joining(","));
        PaymentGateway.ChargeResult result = gateway.charge(card, amount, "USD", "Hotel booking " + refs);

        Payment payment = new Payment();
        payment.setTransactionId(result.transactionId());
        payment.setUsername(username);
        payment.setBookingReferences(refs);
        payment.setAmount(amount);
        payment.setCardBrand(card.brand());
        payment.setCardLast4(card.last4());
        payment.setCardHolder(card.getHolder());
        payment.setStatus(result.approved() ? Payment.SUCCEEDED : Payment.DECLINED);
        payment.setFailureReason(result.declineReason());
        paymentRepository.save(payment);

        if (!result.approved()) {
            attemptLimiter.recordFailure(username, Instant.now());
            return new PaymentOutcome(false, result.declineReason() + " You have not been charged.", payment);
        }
        attemptLimiter.reset(username);

        List<Booking> confirmed = new ArrayList<>();
        for (Booking b : bookings) {
            b.setAmountPaid(b.getFinalAmount());
            b.setBalanceDue(0.0);
            b.setStatus("APPROVED");
            b.setStatusNote(null);
            b.setRoomReleased(false);
            b.setRoomReleasedAt(null);
            b.setPaymentMethod("CARD");
            b.setTransactionId(payment.getTransactionId());
            confirmed.add(bookingRepository.save(b));
        }
        // Observer: receipt email, guest + manager notifications and audit log
        events.publishEvent(new BookingEvent(confirmed, BookingEvent.Type.PAID_ONLINE, username, payment.getTransactionId()));
        return new PaymentOutcome(true, "Payment successful - your booking is confirmed.", payment);
    }
}
