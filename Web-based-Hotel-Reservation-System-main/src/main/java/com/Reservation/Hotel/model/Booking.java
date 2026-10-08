package com.Reservation.Hotel.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;

    private String username;

    // Human-readable booking reference shown to guests and in emails, e.g. BK-7F3A21C9
    @Column(unique = true, length = 20)
    private String reference;

    // Shared by all rooms booked together in one multi-room booking (null for single-room bookings)
    @Column(length = 20)
    private String groupReference;

    private LocalDateTime createdAt;
    private int guests;            // total = adults + children (kept so older code keeps working)
    private Integer adults;         // null on bookings made before adults/children were split
    private Integer children;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;

    // Pricing & Discounts (reflects the CURRENT dates/room, recalculated on every edit)
    private double originalAmount;
    private double discountAmount;
    private double finalAmount;
    private String promoCode;

    // How the guest paid: CARD (online, confirmed instantly) or BANK_TRANSFER (receipt checked by the manager)
    @Column(length = 20)
    private String paymentMethod;

    // Gateway transaction id of the online payment (see Payment)
    @Column(length = 20)
    private String transactionId;

    // Which discount was applied, e.g. "Coupon SAVE10 (10%)" - set by PricingService (Strategy pattern)
    @Column(length = 150)
    private String appliedDiscount;

    // Id of a standard Promotion (type DISCOUNT) the guest selected from the dropdown at
    // booking time, as an alternative to typing a coupon code. Nullable - no discount picked.
    private Long discountId;

    // Payment tracking
    // amountPaid is only ever set by a manager action (approve / settle-refund / accept-refund) -
    // it represents money the hotel has actually confirmed, never what the user merely claims.
    private double amountPaid;

    // balanceDue = finalAmount - amountPaid
    // > 0  => guest still owes this much (top-up payment required)
    // < 0  => hotel owes the guest a refund of this amount
    // ~ 0  => settled
    private double balanceDue;

    // Payment File Upload (most recent receipt: initial payment OR a top-up payment)
    private String receiptImagePath;

    // Statuses: PENDING, APPROVED, REFUND_PENDING, REFUND_COMPLETED, REFUND_ACCEPTED, CANCELLED
    private String status;

    // General explanation shown to the user for the current status - covers a manager
    // rejection, a refund triggered by a shortened edit, or a cancellation summary.
    @Column(length = 1000)
    private String statusNote;
    private String refundId;

    // Set to true when the manager marks the room as available again BEFORE the check-out date
    // (e.g. the guest left early). The booking itself (status, money, dates) is left untouched -
    // only the room hold is lifted.
    private boolean roomReleased = false;
    private LocalDateTime roomReleasedAt;

    public Booking() {}

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    /** Number of nights in the stay (0 when the dates are missing). */
    @Transient
    public long getNights() {
        if (checkInDate == null || checkOutDate == null) return 0;
        return Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(checkInDate, checkOutDate));
    }

    /** Amount the guest still has to pay (0 when settled or when a refund is due instead). */
    public double getAmountToPay() {
        return Math.max(0.0, Math.round((finalAmount - amountPaid) * 100.0) / 100.0);
    }

    public String getAppliedDiscount() { return appliedDiscount; }
    public void setAppliedDiscount(String appliedDiscount) { this.appliedDiscount = appliedDiscount; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getGroupReference() { return groupReference; }
    public void setGroupReference(String groupReference) { this.groupReference = groupReference; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** Reference to show in the UI - older bookings made before references existed fall back to their id. */
    public String getDisplayReference() {
        return reference != null ? reference : "#" + id;
    }

    // Getters and Setters...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Hotel getHotel() { return hotel; }
    public void setHotel(Hotel hotel) { this.hotel = hotel; }
    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public int getGuests() { return guests; }
    public void setGuests(int guests) { this.guests = guests; }
    public Integer getAdults() { return adults; }
    public void setAdults(Integer adults) { this.adults = adults; }
    public Integer getChildren() { return children; }
    public void setChildren(Integer children) { this.children = children; }

    /** e.g. "2 Adults, 1 Child" - falls back to the plain total for older bookings. */
    public String getGuestSummary() {
        if (adults == null) {
            return guests + (guests == 1 ? " Guest" : " Guests");
        }
        String text = adults + (adults == 1 ? " Adult" : " Adults");
        if (children != null && children > 0) {
            text += ", " + children + (children == 1 ? " Child" : " Children");
        }
        return text;
    }
    public LocalDate getCheckInDate() { return checkInDate; }
    public void setCheckInDate(LocalDate checkInDate) { this.checkInDate = checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }
    public void setCheckOutDate(LocalDate checkOutDate) { this.checkOutDate = checkOutDate; }
    public double getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(double originalAmount) { this.originalAmount = originalAmount; }
    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }
    public double getFinalAmount() { return finalAmount; }
    public void setFinalAmount(double finalAmount) { this.finalAmount = finalAmount; }
    public String getPromoCode() { return promoCode; }
    public void setPromoCode(String promoCode) { this.promoCode = promoCode; }
    public Long getDiscountId() { return discountId; }
    public void setDiscountId(Long discountId) { this.discountId = discountId; }
    public double getAmountPaid() { return amountPaid; }
    public void setAmountPaid(double amountPaid) { this.amountPaid = amountPaid; }
    public double getBalanceDue() { return balanceDue; }
    public void setBalanceDue(double balanceDue) { this.balanceDue = balanceDue; }
    public String getReceiptImagePath() { return receiptImagePath; }
    public void setReceiptImagePath(String receiptImagePath) { this.receiptImagePath = receiptImagePath; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusNote() { return statusNote; }
    public void setStatusNote(String statusNote) { this.statusNote = statusNote; }
    public String getRefundId() { return refundId; }
    public void setRefundId(String refundId) { this.refundId = refundId; }
    public boolean isRoomReleased() { return roomReleased; }
    public void setRoomReleased(boolean roomReleased) { this.roomReleased = roomReleased; }
    public LocalDateTime getRoomReleasedAt() { return roomReleasedAt; }
    public void setRoomReleasedAt(LocalDateTime roomReleasedAt) { this.roomReleasedAt = roomReleasedAt; }

    /**
     * True while this booking is holding its room: the manager has approved it, the manager has not
     * released the room early, and the check-out date has not passed yet.
     * (BookingRepository's "active reservation" queries use exactly the same rule - keep them in sync.)
     */
    public boolean isRoomHeld() {
        return "APPROVED".equals(status)
                && !roomReleased
                && checkOutDate != null
                && !checkOutDate.isBefore(LocalDate.now());
    }
}