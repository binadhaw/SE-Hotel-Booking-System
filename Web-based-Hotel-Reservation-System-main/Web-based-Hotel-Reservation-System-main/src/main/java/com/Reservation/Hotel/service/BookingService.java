package com.Reservation.Hotel.service;

import com.Reservation.Hotel.model.Booking;
import com.Reservation.Hotel.model.Promotion;
import com.Reservation.Hotel.model.Room;
import com.Reservation.Hotel.pricing.AppliedDiscount;
import com.Reservation.Hotel.pricing.PricingService;
import com.Reservation.Hotel.repository.BookingRepository;
import com.Reservation.Hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final PricingService pricingService;

    public BookingService(BookingRepository bookingRepository,
                          RoomRepository roomRepository,
                          PricingService pricingService) {
        this.pricingService = pricingService;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    /** Group reference shared by rooms booked together, e.g. GR-4C1D9E02. */
    public String newGroupReference() {
        return "GR-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    /**
     * Spreads a party over several rooms: every room gets one adult, then the remaining adults and
     * children fill rooms up to their recommended capacity; anyone left over goes into the biggest room.
     * @return per room {adults, children}
     */
    public static int[][] splitGuests(List<Room> rooms, int adults, int children) {
        int n = rooms.size();
        int[][] split = new int[n][2];
        int[] cap = new int[n];
        for (int i = 0; i < n; i++) {
            Integer g = rooms.get(i).getMaxGuests();
            cap[i] = (g == null || g < 1) ? 2 : g;
            split[i][0] = 1;
        }
        int leftAdults = adults - n, leftChildren = children;
        for (int i = 0; i < n; i++) {
            while (leftAdults > 0 && split[i][0] + split[i][1] < cap[i]) { split[i][0]++; leftAdults--; }
            while (leftChildren > 0 && split[i][0] + split[i][1] < cap[i]) { split[i][1]++; leftChildren--; }
        }
        int biggest = 0;
        for (int i = 1; i < n; i++) if (cap[i] > cap[biggest]) biggest = i;
        split[biggest][0] += Math.max(leftAdults, 0);
        split[biggest][1] += Math.max(leftChildren, 0);
        return split;
    }


    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    public List<Booking> getBookingsForManager(String managerUsername) {
        return bookingRepository.findByHotelManagerUsername(managerUsername);
    }

    public List<Booking> getBookingsByUsername(String username) {
        return bookingRepository.findByUsername(username);
    }

    public Booking saveBooking(Booking booking) {
        return bookingRepository.save(booking);
    }

    /** A new unique booking reference such as BK-7F3A21C9. */
    public String newReference() {
        return "BK-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    public Booking getBookingById(Long id) {
        return bookingRepository.findById(id).orElse(null);
    }

    /**
     * Statuses in which the booking still represents "live money" - i.e. either the
     * hotel or the guest may still owe something, or a decision is still pending.
     * Hard-deleting a booking in one of these statuses would destroy a financial record.
     */
    private static final List<String> CLOSED_STATUSES = List.of(
            "CANCELLED", "REFUND_COMPLETED", "REFUND_ACCEPTED"
    );

    public boolean isClosed(Booking booking) {
        return booking != null && CLOSED_STATUSES.contains(booking.getStatus());
    }

    /**
     * Manager action: lift the room hold on an APPROVED booking before its check-out date
     * (e.g. the guest left early). Only the room hold is lifted - dates, price, payments and
     * status of the booking are NOT changed.
     *
     * @return null when released, otherwise a message explaining why it could not be done
     */
    public String releaseRoom(Booking booking) {
        if (booking == null) return "Booking not found.";
        if (!"APPROVED".equals(booking.getStatus())) {
            return "Only an approved booking holds a room.";
        }
        if (booking.isRoomReleased()) {
            return "This room has already been released.";
        }
        if (booking.getCheckOutDate() != null && booking.getCheckOutDate().isBefore(java.time.LocalDate.now())) {
            return "The check-out date has already passed - the room is available again automatically.";
        }
        if (booking.getCheckInDate() != null && booking.getCheckInDate().isAfter(java.time.LocalDate.now())) {
            // Otherwise a stay that never happened would count as completed (and could be reviewed)
            return "The guest has not checked in yet - cancel the booking instead of releasing the room.";
        }
        booking.setRoomReleased(true);
        booking.setRoomReleasedAt(java.time.LocalDateTime.now());
        bookingRepository.save(booking);
        return null;
    }

    /**
     * Manager action: cancel a PENDING or APPROVED booking (e.g. the guest left before the check-out
     * date). The room is freed because the booking is no longer APPROVED.
     *
     * @param refund when true and money was confirmed paid, the full paid amount goes through the
     *               normal refund pipeline (REFUND_PENDING); otherwise the booking is simply CANCELLED
     *               and the payment record is kept as it was.
     * @return null when cancelled, otherwise a message explaining why it could not be done
     */
    public String managerCancelBooking(Booking booking, String reason, boolean refund) {
        if (booking == null) return "Booking not found.";
        if (!"PENDING".equals(booking.getStatus()) && !"APPROVED".equals(booking.getStatus())) {
            return "Only pending or approved bookings can be cancelled.";
        }
        String note = (reason == null || reason.isBlank())
                ? "Cancelled by the hotel manager."
                : "Cancelled by the hotel manager: " + reason.trim();

        if (refund && booking.getAmountPaid() > 0.0) {
            booking.setStatus("REFUND_PENDING");
            booking.setBalanceDue(-booking.getAmountPaid());
            booking.setRefundId("REF-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            booking.setStatusNote(note + String.format(" A refund of $%.2f is due.", booking.getAmountPaid()));
        } else {
            booking.setStatus("CANCELLED");
            booking.setBalanceDue(0.0);
            booking.setStatusNote(note);
        }
        bookingRepository.save(booking);
        return null;
    }

    /**
     * Another APPROVED booking that is currently holding the same room as {@code booking}
     * (so approving {@code booking} would double-book the room), or null when the room is free.
     */
    public Booking findConflictingReservation(Booking booking) {
        if (booking == null || booking.getRoom() == null) return null;
        return bookingRepository
                .findApprovedOverlappingForRoom(booking.getRoom().getId(), booking.getCheckInDate(), booking.getCheckOutDate())
                .stream()
                .filter(b -> !b.getId().equals(booking.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Locks the rooms of these bookings until the caller's transaction ends, so the "is the room still free?"
     * check and the confirmation that follows it cannot interleave with another guest confirming the same room.
     * Without it, two simultaneous payments both see the room as free and both get approved.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockRooms(Collection<Booking> bookings) {
        List<Long> roomIds = bookings.stream()
                .map(Booking::getRoom).filter(Objects::nonNull)
                .map(Room::getId).distinct().toList();
        if (!roomIds.isEmpty()) roomRepository.lockAllById(roomIds);
    }

    /**
     * Manager approval: confirms the money as received and reserves the room, unless another approved booking
     * already holds it - checked under the room lock so two approvals cannot both win.
     * @return the clashing booking, or null when this booking was approved
     */
    @Transactional
    public Booking approve(Booking booking) {
        lockRooms(List.of(booking));
        Booking conflict = findConflictingReservation(booking);
        if (conflict != null) return conflict;
        booking.setAmountPaid(booking.getFinalAmount());
        booking.setBalanceDue(0.0);
        booking.setStatus("APPROVED");
        booking.setStatusNote(null);
        booking.setRoomReleased(false);
        booking.setRoomReleasedAt(null);
        bookingRepository.save(booking);
        return null;
    }

    /**
     * Saves a guest's edit. An edit that keeps the booking APPROVED (same price, new room or dates) reserves the
     * new nights straight away, so the room is re-checked under its lock; on a clash nothing is saved.
     * @return the clashing booking, or null when the edit was saved
     */
    @Transactional
    public Booking saveEdit(Booking booking) {
        if ("APPROVED".equals(booking.getStatus())) {
            lockRooms(List.of(booking));
            Booking conflict = findConflictingReservation(booking);
            if (conflict != null) {
                // Roll back; Spring also clears the request's persistence context, discarding the edited fields
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return conflict;
            }
        }
        bookingRepository.save(booking);
        return null;
    }

    /** Only ever call this for bookings already in a CLOSED status - see isClosed(). */
    public void deleteBookingById(Long id) {
        bookingRepository.deleteById(id);
    }

    /**
     * A booking can still be changed by the guest only if:
     *  - it hasn't already entered a refund/cancelled state, and
     *  - the stay hasn't started yet.
     */
    public boolean isEditable(Booking booking) {
        if (booking == null) return false;
        if (!"PENDING".equals(booking.getStatus()) && !"APPROVED".equals(booking.getStatus())) {
            return false;
        }
        // The manager already handed the room back (guest left early) - the stay is over.
        if (booking.isRoomReleased()) return false;
        return booking.getCheckInDate() != null && !booking.getCheckInDate().isBefore(java.time.LocalDate.now());
    }

    /**
     * Recalculates price for (possibly new) dates/room/promo and reconciles it against
     * whatever the hotel has already confirmed as paid (booking.getAmountPaid()).
     *
     * Does NOT touch amountPaid itself - only a manager action confirms money received.
     * Sets finalAmount/discountAmount/originalAmount/balanceDue and adjusts status:
     *   - unchanged price & already APPROVED -> stays APPROVED
     *   - price increased on an APPROVED booking -> back to PENDING (needs re-approval + top-up payment)
     *   - price decreased on an APPROVED booking -> REFUND_PENDING (existing refund pipeline)
     *   - still PENDING (never confirmed) -> stays PENDING regardless of direction
     */
    public void recalculatePricingForEdit(Booking booking, Room room) {
        long nights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        if (nights <= 0) nights = 1;

        double pricePerNight = room.getPrice() != null ? room.getPrice() : 0.0;
        double originalTotal = pricePerNight * nights;
        // Strategy pattern: PricingService asks every DiscountStrategy and applies the best one
        AppliedDiscount best = pricingService.bestDiscount(booking);
        double discount = originalTotal * best.rate();
        booking.setAppliedDiscount(best.label());

        double newFinal = originalTotal - discount;
        double balance = newFinal - booking.getAmountPaid();

        booking.setOriginalAmount(originalTotal);
        booking.setDiscountAmount(discount);
        booking.setFinalAmount(newFinal);
        booking.setBalanceDue(balance);

        boolean wasApproved = "APPROVED".equals(booking.getStatus());

        if (!wasApproved) {
            // Nothing confirmed paid yet - just update the numbers, keep awaiting first approval.
            booking.setStatus("PENDING");
            booking.setStatusNote(null);
            return;
        }

        if (Math.abs(balance) < 0.01) {
            // Same price - no reconciliation needed.
            booking.setStatus("APPROVED");
            booking.setStatusNote(null);
        } else if (balance > 0) {
            // Guest now owes more - needs to pay the difference and be re-approved.
            booking.setStatus("PENDING");
            booking.setStatusNote(String.format(
                    "Dates changed - an additional payment of $%.2f is required. " +
                            "Please upload a receipt for the difference and wait for manager approval.", balance));
        } else {
            // Guest is owed money back - route through the existing refund pipeline.
            booking.setStatus("REFUND_PENDING");
            booking.setRefundId("REF-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            booking.setStatusNote(String.format(
                    "Dates changed - a refund of $%.2f is due for the shortened stay.", -balance));
        }
    }


    /**
     * Cancels a booking. If nothing has been confirmed as paid yet, closes it out directly.
     * If money was already confirmed paid, routes the full amount through the refund pipeline
     * instead of deleting anything.
     */
    public void cancelBooking(Long id) {
        Booking booking = getBookingById(id);
        if (booking == null) return;

        if (booking.getAmountPaid() <= 0.0) {
            booking.setStatus("CANCELLED");
            booking.setStatusNote("Cancelled by guest before any payment was confirmed.");
        } else {
            booking.setStatus("REFUND_PENDING");
            booking.setBalanceDue(-booking.getAmountPaid());
            booking.setRefundId("REF-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            booking.setStatusNote(String.format(
                    "Cancelled by guest. A refund of $%.2f is due.", booking.getAmountPaid()));
        }
        bookingRepository.save(booking);
    }
}