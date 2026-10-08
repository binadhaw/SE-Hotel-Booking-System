package com.Reservation.Hotel.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One online card payment attempt (successful or declined). For security only the card brand and the
 * last four digits are kept - the full card number and the CVV are never stored.
 */
@Entity
@Table(name = "payments")
public class Payment {

    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String DECLINED = "DECLINED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Gateway transaction id shown on the receipt, e.g. TXN-8F2A1C77D0
    @Column(nullable = false, unique = true, length = 20)
    private String transactionId;

    @Column(nullable = false, length = 50)
    private String username;

    // Booking references paid for, comma separated (several for a multi-room booking)
    @Column(nullable = false, length = 500)
    private String bookingReferences;

    private double amount;

    @Column(length = 3)
    private String currency = "USD";

    @Column(length = 20)
    private String cardBrand;

    @Column(length = 4)
    private String cardLast4;

    // Name on card (personal data - encrypted at rest, see Phase 13.5)
    @Column(length = 255)
    @Convert(converter = com.Reservation.Hotel.security.EncryptedStringConverter.class)
    private String cardHolder;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 200)
    private String failureReason;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Payment() {}

    public Long getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getBookingReferences() { return bookingReferences; }
    public void setBookingReferences(String bookingReferences) { this.bookingReferences = bookingReferences; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCardBrand() { return cardBrand; }
    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }
    public String getCardLast4() { return cardLast4; }
    public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }
    public String getCardHolder() { return cardHolder; }
    public void setCardHolder(String cardHolder) { this.cardHolder = cardHolder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** e.g. "Visa •••• 4242" */
    public String getCardLabel() {
        return (cardBrand == null ? "Card" : cardBrand) + " •••• " + (cardLast4 == null ? "" : cardLast4);
    }
}
