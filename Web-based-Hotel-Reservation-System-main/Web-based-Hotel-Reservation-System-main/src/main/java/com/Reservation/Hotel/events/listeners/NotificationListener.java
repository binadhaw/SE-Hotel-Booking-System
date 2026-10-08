package com.Reservation.Hotel.events.listeners;

import com.Reservation.Hotel.events.BookingEvent;
import com.Reservation.Hotel.events.HotelEvent;
import com.Reservation.Hotel.events.ReviewEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer: turns domain events into in-app notifications and emails for the people affected
 * (guest, hotel manager). Adding e.g. SMS alerts later means adding another listener, not editing
 * the booking, hotel or review code.
 */
@Component
public class NotificationListener {

    private final NotificationService notificationService;
    private final EmailService emailService;

    public NotificationListener(NotificationService notificationService, EmailService emailService) {
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    // ---------------- bookings ----------------

    @EventListener
    public void onBooking(BookingEvent e) {
        Booking b = e.first();
        Hotel hotel = b.getHotel();
        String ref = b.getDisplayReference();
        switch (e.type()) {
            case CREATED -> {
                // Card bookings are confirmed (and announced) by PAID_ONLINE once the payment goes through
                if ("CARD".equals(b.getPaymentMethod())) return;
                // Guest: one confirmation for the whole group, with the price breakdown
                StringBuilder html = new StringBuilder("<h2>We received your booking request</h2>"
                        + "<p>The hotel will review it shortly. You will get another message once it is approved.</p>");
                double total = 0;
                for (Booking each : e.bookings()) {
                    html.append(EmailService.bookingTable(each, "Amount due")).append("<br>");
                    total += each.getFinalAmount();
                }
                if (e.bookings().size() > 1) {
                    html.append("<p><strong>Group ").append(EmailService.esc(b.getGroupReference()))
                            .append(" total: ").append(String.format("$%.2f", total)).append("</strong></p>");
                }
                emailService.sendToUser(b.getUsername(), "Booking request received - " + hotel.getName(), html.toString());
                notificationService.notifyInApp(b.getUsername(), "Booking request received",
                        e.bookings().size() + " room(s) at " + hotel.getName() + " are awaiting the hotel's approval.", "/bookings");
                // Manager: new request to review
                notificationService.notify(hotel.getManagerUsername(), "New booking request - " + hotel.getName(),
                        e.bookings().size() + " room(s) requested by " + b.getUsername() + " for "
                                + b.getCheckInDate() + " to " + b.getCheckOutDate() + ".", "/bookings");
            }
            case APPROVED -> {
                emailService.sendBookingReceipt(b);
                notificationService.notifyInApp(b.getUsername(), "Booking confirmed - " + hotel.getName(),
                        "Your booking " + ref + " (" + b.getCheckInDate() + " to " + b.getCheckOutDate()
                                + ") has been approved. Enjoy your stay!", "/bookings");
            }
            case PAID_ONLINE -> {
                // Online card payment confirms the stay instantly: receipt to the guest, heads-up to the manager
                for (Booking each : e.bookings()) emailService.sendBookingReceipt(each);
                notificationService.notifyInApp(b.getUsername(), "Payment received - booking confirmed",
                        e.bookings().size() + " room(s) at " + hotel.getName() + " are confirmed. Transaction " + e.note() + ".", "/bookings");
                notificationService.notifyInApp(hotel.getManagerUsername(), "Booking paid online - " + hotel.getName(),
                        e.bookings().size() + " room(s) for " + b.getCheckInDate() + " to " + b.getCheckOutDate()
                                + " were paid by card and confirmed automatically (" + e.note() + ").", "/bookings");
            }
            case REJECTED -> notificationService.notify(b.getUsername(), "Booking declined - " + hotel.getName(),
                    "Your booking " + ref + " was declined by the hotel. Reason: " + e.note()
                            + (b.getAmountPaid() > 0 ? " Your refund is being processed." : ""), "/bookings");
            case CANCELLED_BY_HOTEL -> notificationService.notify(b.getUsername(), "Booking cancelled - " + hotel.getName(),
                    "Your booking " + ref + " was cancelled by the hotel. " + (b.getStatusNote() == null ? "" : b.getStatusNote()), "/bookings");
            case CANCELLED_BY_GUEST -> notificationService.notifyInApp(hotel.getManagerUsername(), "Booking cancelled by guest - " + hotel.getName(),
                    ref + " (" + b.getCheckInDate() + " to " + b.getCheckOutDate() + ") was cancelled by " + b.getUsername() + ".", "/bookings");
            case REFUND_COMPLETED -> notificationService.notifyInApp(b.getUsername(), "Refund completed - " + hotel.getName(),
                    "The refund " + b.getRefundId() + " for booking " + ref + " has been paid. Please confirm you received it.", "/bookings");
        }
    }

    // ---------------- hotels ----------------

    @EventListener
    public void onHotel(HotelEvent e) {
        Hotel h = e.hotel();
        switch (e.type()) {
            case APPROVED -> notificationService.notify(h.getManagerUsername(), "Hotel approved: " + h.getName(),
                    h.getName() + " is now live and visible to tourists.", "/hotels/" + h.getId());
            case REJECTED -> notificationService.notify(h.getManagerUsername(), "Hotel registration rejected: " + h.getName(),
                    "Reason: " + e.note() + " You can update the details and it will be re-submitted.", "/hotels/" + h.getId() + "/edit");
            case REMOVED -> notificationService.notify(h.getManagerUsername(), "Hotel removed: " + h.getName(),
                    e.note() == null ? "" : e.note(), "/hotels");
            case REGISTERED -> { /* nothing to notify - admins see it in the approval queue */ }
        }
    }

    // ---------------- reviews ----------------

    @EventListener
    public void onReview(ReviewEvent e) {
        Review r = e.review();
        Hotel h = r.getHotel();
        switch (e.type()) {
            case POSTED -> notificationService.notifyInApp(h.getManagerUsername(), "New " + r.getRating() + "-star review - " + h.getName(),
                    (r.getTitle() != null ? r.getTitle() + ": " : "") + (r.getComment() == null ? "" : r.getComment()),
                    "/hotels/" + h.getId() + "#reviews");
            case REPLIED -> notificationService.notify(r.getUsername(), "The hotel replied to your review - " + h.getName(),
                    r.getManagerReply(), "/hotels/" + h.getId() + "#reviews");
            case HIDDEN, RESTORED -> { /* moderation is only audited */ }
        }
    }
}
