package com.Reservation.Hotel.service;

import com.Reservation.Hotel.model.Booking;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends HTML emails. When app.mail.enabled=false (the default for local development) the
 * message is only written to the log, so the app works without an SMTP server.
 * Sending never throws - a failed email must not break a booking or approval.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final UserService userService;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:no-reply@hotel.local}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender, UserService userService) {
        this.mailSender = mailSender;
        this.userService = userService;
    }

    /** @return true when the email was handed to the mail server (or logged in dev mode) */
    public boolean send(String to, String subject, String htmlBody) {
        if (to == null || to.isBlank()) {
            log.warn("Email '{}' not sent: no recipient address", subject);
            return false;
        }
        if (!mailEnabled) {
            log.info("[mail disabled] To: {} | Subject: {}", to, subject);
            return true;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(wrap(subject, htmlBody), true);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("Could not send email '{}' to {}: {}", subject, to, e.getMessage());
            return false;
        }
    }

    /** Sends to the email address registered for this username. */
    public boolean sendToUser(String username, String subject, String htmlBody) {
        return send(userService.emailOf(username), subject, htmlBody);
    }

    public boolean sendBookingReceipt(Booking booking) {
        String content = "<h2>Booking Approved!</h2>"
                + "<p>Dear " + esc(userService.displayNameOf(booking.getUsername())) + ", your booking is confirmed. "
                + "Here is your payment receipt:</p>"
                + bookingTable(booking, "Total Paid");
        return sendToUser(booking.getUsername(),
                "Booking Approved & Payment Receipt - " + booking.getHotel().getName(), content);
    }

    /** Escapes user-supplied text for safe inclusion in an HTML email. */
    public static String esc(Object value) {
        return value == null ? "" : HtmlUtils.htmlEscape(String.valueOf(value));
    }

    public static String bookingTable(Booking booking, String amountLabel) {
        String ref = booking.getReference() != null ? booking.getReference() : "#" + booking.getId();
        String room = booking.getRoom() != null
                ? esc(booking.getRoom().getRoomType()) + " (Room " + esc(booking.getRoom().getRoomNumber()) + ")" : "-";
        return "<table cellpadding='8' style='border-collapse:collapse;border:1px solid #ddd;'>"
                + row("Booking reference", esc(ref))
                + row("Hotel", esc(booking.getHotel().getName()) + ", " + esc(booking.getHotel().getLocation()))
                + row("Room", room)
                + row("Check-in", esc(booking.getCheckInDate()))
                + row("Check-out", esc(booking.getCheckOutDate()))
                + row("Guests", esc(booking.getGuestSummary()))
                + row(amountLabel, "<strong>" + String.format("$%.2f", booking.getFinalAmount()) + "</strong>")
                + "</table>";
    }

    private static String row(String label, String value) {
        return "<tr><td style='border:1px solid #ddd;'><strong>" + label + "</strong></td>"
                + "<td style='border:1px solid #ddd;'>" + value + "</td></tr>";
    }

    private String wrap(String title, String body) {
        return "<div style='font-family:Arial,sans-serif;max-width:620px;margin:auto;color:#0F172A'>"
                + "<div style='background:#0F172A;color:#C59B27;padding:16px 20px;font-weight:bold;letter-spacing:1px'>"
                + "AURA GRAND LUXE &bull; Hotel Reservations</div>"
                + "<div style='padding:20px;border:1px solid #E2E8F0'>" + body + "</div>"
                + "<p style='font-size:12px;color:#64748B;padding:0 20px'>This is an automated message about: "
                + esc(title) + "</p></div>";
    }
}
