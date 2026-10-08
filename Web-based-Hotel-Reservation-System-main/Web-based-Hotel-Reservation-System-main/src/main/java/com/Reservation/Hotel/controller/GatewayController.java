package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.payment.CardDetails;
import com.Reservation.Hotel.payment.HostedCheckoutService;
import com.Reservation.Hotel.payment.HostedPaymentSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * The hosted payment page of the demo gateway "AuraPay". It is deliberately a separate, self-contained page
 * (its own look, no hotel navigation) so the guest experiences the redirect to a payment provider and back.
 */
@Controller
@RequestMapping("/gateway")
public class GatewayController {

    private final HostedCheckoutService hostedCheckout;

    public GatewayController(HostedCheckoutService hostedCheckout) {
        this.hostedCheckout = hostedCheckout;
    }

    @GetMapping("/{id:[a-f0-9]{32}}")
    public String page(@PathVariable String id, Authentication authentication, Model model) {
        HostedPaymentSession s = hostedCheckout.find(id, authentication.getName(), Instant.now());
        if (s == null) return "gateway/missing";
        if (s.isFinished()) return "redirect:/payments/return/" + id;
        return render(s, authentication, model);
    }

    @PostMapping("/{id:[a-f0-9]{32}}/card")
    public String card(@PathVariable String id,
                       @RequestParam(value = "cardHolder", required = false) String cardHolder,
                       @RequestParam(value = "cardNumber", required = false) String cardNumber,
                       @RequestParam(value = "expiry", required = false) String expiry,
                       @RequestParam(value = "cvv", required = false) String cvv,
                       Authentication authentication, Model model) {
        HostedPaymentSession s = hostedCheckout.find(id, authentication.getName(), Instant.now());
        if (s == null) return "gateway/missing";
        if (s.isFinished()) return "redirect:/payments/return/" + id;
        CardDetails.Problem problem = hostedCheckout.submitCard(s, new CardDetails(cardHolder, cardNumber, expiry, cvv), Instant.now());
        if (problem != null) {
            // Show the form again; the card number and CVV are deliberately not sent back to the page
            model.addAttribute("errorMessage", problem.message());
            model.addAttribute("errorField", problem.field());
            model.addAttribute("cardHolder", cardHolder);
            return render(s, authentication, model);
        }
        return "redirect:/gateway/" + id;
    }

    @PostMapping("/{id:[a-f0-9]{32}}/otp")
    public String otp(@PathVariable String id, @RequestParam(value = "code", required = false) String code,
                      Authentication authentication, Model model) {
        HostedPaymentSession s = hostedCheckout.find(id, authentication.getName(), Instant.now());
        if (s == null) return "gateway/missing";
        String error = hostedCheckout.confirmOtp(s, code, Instant.now());
        if (error != null) {
            model.addAttribute("errorMessage", error);
            return render(s, authentication, model);
        }
        return s.isFinished() ? "redirect:/payments/return/" + id : "redirect:/gateway/" + id;
    }

    @PostMapping("/{id:[a-f0-9]{32}}/change-card")
    public String changeCard(@PathVariable String id, Authentication authentication) {
        HostedPaymentSession s = hostedCheckout.find(id, authentication.getName(), Instant.now());
        if (s == null) return "gateway/missing";
        hostedCheckout.changeCard(s);
        return "redirect:/gateway/" + id;
    }

    @PostMapping("/{id:[a-f0-9]{32}}/cancel")
    public String cancel(@PathVariable String id, Authentication authentication) {
        HostedPaymentSession s = hostedCheckout.find(id, authentication.getName(), Instant.now());
        if (s == null) return "gateway/missing";
        hostedCheckout.cancel(s);
        return "redirect:/payments/return/" + id;
    }

    private String render(HostedPaymentSession s, Authentication authentication, Model model) {
        Instant now = Instant.now();
        model.addAttribute("pay", s);
        model.addAttribute("secondsLeft", s.secondsLeft(now));
        model.addAttribute("attemptsLeft", hostedCheckout.attemptsLeft(authentication.getName(), now));
        return "gateway/pay";
    }
}
