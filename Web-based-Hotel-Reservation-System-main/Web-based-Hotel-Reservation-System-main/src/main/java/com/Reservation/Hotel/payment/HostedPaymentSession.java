package com.Reservation.Hotel.payment;

import java.time.Instant;
import java.util.List;

/**
 * One visit to the hosted payment page (redirect flow): the hotel site creates the session, the guest is
 * redirected to the gateway page, pays there, and is sent back to the hotel site, which reads the outcome from
 * this session on the server (never from the browser). Card details live here only between the card step and
 * the one-time-code step, then they are dropped.
 */
public class HostedPaymentSession {

    public enum Status { OPEN, AWAITING_OTP, PAID, FAILED, CANCELLED, EXPIRED }

    /** A line on the gateway's order summary. */
    public record LineItem(String title, String detail, double amount) {}

    private final String id;
    private final String username;
    private final List<Long> bookingIds;
    private final List<LineItem> items;
    private final double amount;
    private final String reference;
    private final Instant createdAt;
    private final Instant expiresAt;

    private volatile Status status = Status.OPEN;
    private CardDetails pendingCard;
    private String otp;
    private int otpAttempts;
    private String cardLabel;
    private String transactionId;
    private String message;

    HostedPaymentSession(String id, String username, List<Long> bookingIds, List<LineItem> items, double amount,
                         String reference, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.username = username;
        this.bookingIds = List.copyOf(bookingIds);
        this.items = List.copyOf(items);
        this.amount = amount;
        this.reference = reference;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public List<Long> getBookingIds() { return bookingIds; }
    public List<LineItem> getItems() { return items; }
    public double getAmount() { return amount; }
    public String getReference() { return reference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Status getStatus() { return status; }
    public String getCardLabel() { return cardLabel; }
    public String getTransactionId() { return transactionId; }
    public String getMessage() { return message; }
    public int getOtpAttemptsLeft() { return Math.max(0, HostedCheckoutService.MAX_OTP_ATTEMPTS - otpAttempts); }

    /** Shown on the gateway page only because this is a demo gateway (a real bank sends it by SMS). */
    public String getDemoOtp() { return otp; }

    public boolean isFinished() {
        return status == Status.PAID || status == Status.FAILED || status == Status.CANCELLED || status == Status.EXPIRED;
    }

    public long secondsLeft(Instant now) {
        return Math.max(0, expiresAt.getEpochSecond() - now.getEpochSecond());
    }

    // ---- state changes (HostedCheckoutService only) ----

    void awaitOtp(CardDetails card, String otp) {
        this.pendingCard = card;
        this.cardLabel = card.brand() + " •••• " + card.last4();
        this.otp = otp;
        this.otpAttempts = 0;
        this.status = Status.AWAITING_OTP;
    }

    CardDetails takeCard() {
        CardDetails c = pendingCard;
        pendingCard = null;
        otp = null;
        return c;
    }

    boolean otpMatches(String code) { return otp != null && otp.equals(code); }
    void wrongOtp() { otpAttempts++; }

    void finish(Status status, String transactionId, String message) {
        this.pendingCard = null;
        this.otp = null;
        this.status = status;
        this.transactionId = transactionId;
        this.message = message;
    }

    void backToCard(String message) {
        this.pendingCard = null;
        this.otp = null;
        this.status = Status.OPEN;
        this.message = message;
    }
}
