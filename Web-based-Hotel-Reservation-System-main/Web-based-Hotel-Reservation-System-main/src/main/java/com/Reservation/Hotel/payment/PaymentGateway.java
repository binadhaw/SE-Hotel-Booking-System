package com.Reservation.Hotel.payment;

/**
 * The card processor. The app talks to this interface only, so the simulated gateway used for the
 * project can be swapped for a real provider (PayHere, Stripe...) without touching the booking code.
 */
public interface PaymentGateway {

    /** Result of a charge attempt. */
    record ChargeResult(boolean approved, String transactionId, String declineReason) {}

    ChargeResult charge(CardDetails card, double amount, String currency, String description);
}
