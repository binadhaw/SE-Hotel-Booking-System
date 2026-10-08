package com.Reservation.Hotel.payment;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Demo gateway - no real money moves. It behaves like a real test environment:
 * <ul>
 *   <li>4000 0000 0000 0002 - declined by the issuer</li>
 *   <li>4000 0000 0000 9995 - declined, insufficient funds</li>
 *   <li>4000 0000 0000 0127 - declined, the bank says the security code is wrong</li>
 *   <li>4000 0000 0000 0069 - declined, the bank reports the card as expired</li>
 *   <li>4000 0000 0000 0119 - processing error at the bank</li>
 *   <li>any other valid card (e.g. 4242 4242 4242 4242) - approved</li>
 * </ul>
 * Amounts above {@link #CARD_LIMIT} are declined as over the card's limit.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    static final double CARD_LIMIT = 50_000;

    @Override
    public ChargeResult charge(CardDetails card, double amount, String currency, String description) {
        String txn = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        if (amount <= 0) return new ChargeResult(false, txn, "Nothing to pay.");
        if (amount > CARD_LIMIT) return new ChargeResult(false, txn, "This payment is over your card's limit.");
        return switch (card.getNumber()) {
            case "4000000000000002" -> new ChargeResult(false, txn, "Your card was declined by the bank.");
            case "4000000000009995" -> new ChargeResult(false, txn, "Insufficient funds on this card.");
            case "4000000000000127" -> new ChargeResult(false, txn, "Your bank says the security code (CVV) is incorrect.");
            case "4000000000000069" -> new ChargeResult(false, txn, "Your bank reports that this card has expired.");
            case "4000000000000119" -> new ChargeResult(false, txn, "The bank could not process the payment - please try again.");
            default -> new ChargeResult(true, txn, null);
        };
    }
}
