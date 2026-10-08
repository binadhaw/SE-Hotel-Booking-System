package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.model.Booking;
import com.Reservation.Hotel.model.Payment;
import com.Reservation.Hotel.payment.HostedCheckoutService;
import com.Reservation.Hotel.payment.HostedPaymentSession;
import com.Reservation.Hotel.payment.PaymentService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Hotel-site side of online card payment (Phase 13.1, redirect flow since Phase 17):
 * review the order, hand over to the hosted payment page, then read the result when the guest comes back.
 * Card details are never posted to the hotel site itself.
 */
@Controller
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final HostedCheckoutService hostedCheckout;

    public PaymentController(PaymentService paymentService, HostedCheckoutService hostedCheckout) {
        this.paymentService = paymentService;
        this.hostedCheckout = hostedCheckout;
    }

    /** Order review - the last page on the hotel site before the gateway. */
    @GetMapping("/checkout")
    public String checkout(@RequestParam(value = "bookingIds", required = false) List<Long> bookingIds,
                           Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        List<Booking> bookings = paymentService.payableBookings(bookingIds, authentication.getName());
        if (bookings.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "There is nothing to pay for those bookings.");
            return "redirect:/bookings";
        }
        model.addAttribute("bookings", bookings);
        model.addAttribute("total", PaymentService.total(bookings));
        model.addAttribute("attemptsLeft", hostedCheckout.attemptsLeft(authentication.getName(), Instant.now()));
        return "payments/checkout";
    }

    /** Opens a payment session and redirects the guest to the hosted payment page. */
    @PostMapping("/checkout/start")
    public String start(@RequestParam("bookingIds") List<Long> bookingIds, Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        List<Booking> bookings = paymentService.payableBookings(bookingIds, authentication.getName());
        if (bookings.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Those bookings no longer need payment.");
            return "redirect:/bookings";
        }
        HostedPaymentSession session = hostedCheckout.create(bookings, authentication.getName(), Instant.now());
        return "redirect:/gateway/" + session.getId();
    }

    /** The gateway sends the guest back here. The outcome is read from the session on the server. */
    @GetMapping("/return/{sessionId}")
    public String returned(@PathVariable String sessionId, Authentication authentication, RedirectAttributes redirectAttributes) {
        HostedPaymentSession s = hostedCheckout.find(sessionId, authentication.getName(), Instant.now());
        if (s == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "We could not find that payment. You have not been charged.");
            return "redirect:/bookings";
        }
        String retry = "redirect:/payments/checkout?" + s.getBookingIds().stream().map(id -> "bookingIds=" + id).collect(Collectors.joining("&"));
        return switch (s.getStatus()) {
            case PAID -> "redirect:/payments/" + s.getTransactionId();
            case OPEN, AWAITING_OTP -> "redirect:/gateway/" + s.getId();
            default -> {
                redirectAttributes.addFlashAttribute("errorMessage", s.getMessage());
                yield paymentService.payableBookings(s.getBookingIds(), authentication.getName()).isEmpty() ? "redirect:/bookings" : retry;
            }
        };
    }

    /** Payment receipt (only for the guest who paid). */
    @GetMapping("/{transactionId:TXN-[A-Z0-9]+}")
    public String receipt(@PathVariable String transactionId, Authentication authentication, Model model,
                          RedirectAttributes redirectAttributes) {
        Payment payment = paymentService.findByTransactionId(transactionId);
        if (payment == null || !payment.getUsername().equals(authentication.getName()) || !Payment.SUCCEEDED.equals(payment.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Receipt not found.");
            return "redirect:/bookings";
        }
        model.addAttribute("payment", payment);
        model.addAttribute("references", List.of(payment.getBookingReferences().split(",")).stream()
                .map(String::trim).collect(Collectors.toList()));
        return "payments/receipt";
    }
}
