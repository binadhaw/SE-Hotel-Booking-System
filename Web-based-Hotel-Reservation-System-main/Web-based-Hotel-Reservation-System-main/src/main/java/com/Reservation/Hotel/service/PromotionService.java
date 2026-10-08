package com.Reservation.Hotel.service;

import com.Reservation.Hotel.model.Promotion;
import com.Reservation.Hotel.repository.PromotionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final EmailService emailService;

    public PromotionService(PromotionRepository promotionRepository, UserService userService,
                            NotificationService notificationService, EmailService emailService) {
        this.promotionRepository = promotionRepository;
        this.userService = userService;
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    /** Is another coupon (not {@code exceptId}) already using this code? */
    public boolean couponCodeTaken(String code, Long exceptId) {
        return promotionRepository.findAll().stream()
                .anyMatch(p -> p.getCode() != null && p.getCode().equalsIgnoreCase(code)
                        && (exceptId == null || !p.getId().equals(exceptId)));
    }

    /**
     * Opted-in tourists this offer is relevant to (proposal: "personalized offers based on tourist preferences").
     * Platform-wide offers go to everyone. A hotel offer goes to tourists whose preferred destination is that
     * town; tourists without a destination get it if the discounted price fits their budget; tourists with no
     * preferences at all get every offer.
     */
    private List<com.Reservation.Hotel.model.AppUser> promoSubscribers(Promotion p) {
        return userService.findByRole(com.Reservation.Hotel.model.AppUser.ROLE_USER).stream()
                .filter(u -> u.isEnabled() && u.isPromoAlerts())
                .filter(u -> isRelevant(p, u))
                .collect(Collectors.toList());
    }

    public static boolean isRelevant(Promotion p, com.Reservation.Hotel.model.AppUser u) {
        if (p.getHotel() == null) return true;
        String town = p.getHotel().getLocation() == null ? "" : p.getHotel().getLocation().toLowerCase(java.util.Locale.ROOT);
        String wanted = u.getPreferredDestination();
        if (wanted != null && !wanted.isBlank()) {
            String w = wanted.trim().toLowerCase(java.util.Locale.ROOT);
            return town.contains(w) || w.contains(town);
        }
        if (u.getBudgetPerNight() != null) {
            Double from = p.getHotel().getFromPrice();
            double pct = p.getDiscountPercentage() == null ? 0 : p.getDiscountPercentage();
            return from == null || from * (1 - pct / 100.0) <= u.getBudgetPerNight();
        }
        return true;
    }

    private String headline(Promotion p) {
        String pct = p.getDiscountPercentage() == null ? "" : String.format("%.0f%% off", p.getDiscountPercentage());
        return pct + (p.getHotel() != null ? " at " + p.getHotel().getName() : " on all hotels");
    }

    /** In-app alert about a new offer to every tourist who opted in (PBI-04). @return how many were alerted */
    public int alertSubscribers(Promotion p) {
        List<com.Reservation.Hotel.model.AppUser> subs = promoSubscribers(p);
        String title = "New offer: " + headline(p);
        String msg = (p.getDescription() == null ? "" : p.getDescription())
                + (p.getType() == Promotion.PromotionType.COUPON ? " - use code " + p.getCode() : "");
        String link = p.getHotel() != null ? "/hotels/" + p.getHotel().getId() : "/promotions";
        subs.forEach(u -> notificationService.notifyInApp(u.getUsername(), title, msg, link));
        return subs.size();
    }

    /**
     * Emails the coupon - code, discount value, terms and expiry date - to every opted-in tourist and
     * drops it in their in-app inbox. @return number of tourists it was sent to
     */
    public int emailCoupon(Promotion p) {
        List<com.Reservation.Hotel.model.AppUser> subs = promoSubscribers(p);
        String expiry = p.getValidUntil() == null ? "No expiry date"
                : p.getValidUntil().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
        String html = "<h2>Your coupon: " + EmailService.esc(headline(p)) + "</h2>"
                + "<p style='font-size:28px;font-weight:bold;letter-spacing:3px;border:2px dashed #C59B27;display:inline-block;padding:8px 18px'>"
                + EmailService.esc(p.getCode()) + "</p>"
                + "<p>" + EmailService.esc(p.getDescription()) + "</p>"
                + "<table cellpadding='6'>"
                + "<tr><td><strong>Discount</strong></td><td>" + EmailService.esc(String.format("%.0f%%", p.getDiscountPercentage())) + "</td></tr>"
                + "<tr><td><strong>Valid at</strong></td><td>" + EmailService.esc(p.getScopeLabel()) + "</td></tr>"
                + "<tr><td><strong>Expires</strong></td><td>" + EmailService.esc(expiry) + "</td></tr>"
                + "<tr><td><strong>Terms</strong></td><td>" + EmailService.esc(p.getTerms() == null ? "One coupon per booking." : p.getTerms()) + "</td></tr>"
                + "</table><p>Enter the code when you book to apply the discount.</p>";
        for (com.Reservation.Hotel.model.AppUser u : subs) {
            emailService.send(u.getEmail(), "Your coupon " + p.getCode() + " - " + headline(p), html);
            notificationService.notifyInApp(u.getUsername(), "Coupon " + p.getCode() + ": " + headline(p),
                    "Expires: " + expiry + ". " + (p.getTerms() == null ? "" : p.getTerms()), "/promotions");
        }
        return subs.size();
    }

    /** Creates a platform-wide coupon if no coupon with this code exists yet (demo data). */
    public void seedCouponIfMissing(String code, double percent, String description) {
        boolean exists = promotionRepository.findAll().stream()
                .anyMatch(p -> p.getCode() != null && p.getCode().equalsIgnoreCase(code));
        if (exists) return;
        Promotion p = new Promotion();
        p.setType(Promotion.PromotionType.COUPON);
        p.setCode(code);
        p.setDiscountPercentage(percent);
        p.setDescription(description);
        p.setTerms("One coupon per booking. Not combinable with other discounts - the better offer is applied.");
        p.setActive(true);
        p.setValidFrom(LocalDateTime.now().minusDays(1));
        p.setCreatedBy("system");
        promotionRepository.save(p);
    }

    public List<Promotion> getAllPromotions() {
        return promotionRepository.findAll();
    }

    public List<Promotion> getPromotionsByType(String typeFilter) {
        if (typeFilter == null || typeFilter.equalsIgnoreCase("ALL")) {
            return promotionRepository.findAll();
        }
        return promotionRepository.findAll().stream()
                .filter(p -> p.getType() != null && p.getType().name().equalsIgnoreCase(typeFilter))
                .collect(Collectors.toList());
    }

    public void savePromotion(Promotion promotion) {
        if (promotion.getValidFrom() == null) {
            promotion.setValidFrom(LocalDateTime.now());
        }
        promotionRepository.save(promotion);
    }

    public void deletePromotion(Long id) {
        promotionRepository.deleteById(id);
    }

    public Optional<Promotion> getPromotionById(Long id) {
        return promotionRepository.findById(id);
    }

    /** What a guest would see right now. */
    public enum PromoState { OFF, SCHEDULED, LIVE, BETWEEN_WINDOWS, EXPIRED }

    public PromoState stateOf(Promotion promo) {
        return stateOf(promo, LocalDateTime.now());
    }

    public PromoState stateOf(Promotion promo, LocalDateTime now) {
        if (promo.getValidUntil() != null && !now.isBefore(promo.getValidUntil())) return PromoState.EXPIRED;
        if (!promo.isActive()) return PromoState.OFF;
        if (promo.getValidFrom() != null && now.isBefore(promo.getValidFrom())) return PromoState.SCHEDULED;
        return isPromotionCurrentlyActive(promo, now) ? PromoState.LIVE : PromoState.BETWEEN_WINDOWS;
    }

    public boolean isExpired(Promotion promo) {
        return stateOf(promo) == PromoState.EXPIRED;
    }

    /**
     * When the offer stops being usable if it is live right now: the end of the current flash window or the
     * end date, whichever comes first. Null when it is not live or never ends. Pages count down to this.
     */
    public LocalDateTime liveUntil(Promotion promo, LocalDateTime now) {
        if (stateOf(promo, now) != PromoState.LIVE) return null;
        LocalDateTime end = promo.getValidUntil();
        if (promo.getType() == Promotion.PromotionType.COUPON && hasFlashWindow(promo)) {
            LocalDateTime start = promo.getValidFrom() != null ? promo.getValidFrom() : now;
            long secondsPassed = Duration.between(start, now).getSeconds();
            long cycle = promo.getIntervalMinutes() * 60L;
            LocalDateTime windowEnd = now.minusSeconds(secondsPassed % cycle).plusMinutes(promo.getDurationMinutes());
            if (end == null || windowEnd.isBefore(end)) end = windowEnd;
        }
        return end;
    }

    /** Start of the next flash window when the coupon is between windows, otherwise null. */
    public LocalDateTime nextWindowStart(Promotion promo, LocalDateTime now) {
        if (stateOf(promo, now) != PromoState.BETWEEN_WINDOWS || !hasFlashWindow(promo)) return null;
        LocalDateTime start = promo.getValidFrom() != null ? promo.getValidFrom() : now;
        long secondsPassed = Duration.between(start, now).getSeconds();
        long cycle = promo.getIntervalMinutes() * 60L;
        LocalDateTime next = now.minusSeconds(secondsPassed % cycle).plusSeconds(cycle);
        return promo.getValidUntil() != null && !next.isBefore(promo.getValidUntil()) ? null : next;
    }

    private static boolean hasFlashWindow(Promotion promo) {
        return promo.getIntervalMinutes() != null && promo.getIntervalMinutes() > 0
                && promo.getDurationMinutes() != null && promo.getDurationMinutes() > 0;
    }

    public boolean isPromotionCurrentlyActive(Promotion promo) {
        return isPromotionCurrentlyActive(promo, LocalDateTime.now());
    }

    public boolean isPromotionCurrentlyActive(Promotion promo, LocalDateTime now) {
        if (!promo.isActive()) return false;

        if (promo.getValidUntil() != null && now.isAfter(promo.getValidUntil())) return false;

        LocalDateTime start = promo.getValidFrom() != null ? promo.getValidFrom() : now;
        if (now.isBefore(start)) return false;

        // Standard Discount (no recurring intervals)
        if (promo.getType() == Promotion.PromotionType.DISCOUNT) {
            return true;
        }

        // Flash Coupon without interval rules
        if (promo.getIntervalMinutes() == null || promo.getIntervalMinutes() <= 0 ||
                promo.getDurationMinutes() == null || promo.getDurationMinutes() <= 0) {
            return true;
        }

        long minutesPassed = Duration.between(start, now).toMinutes();
        long elapsedInCurrentCycle = minutesPassed % promo.getIntervalMinutes();

        return elapsedInCurrentCycle < promo.getDurationMinutes();
    }

    public List<Promotion> getActiveCouponsForUser() {
        return promotionRepository.findAll().stream()
                .filter(p -> p.getType() == Promotion.PromotionType.COUPON)
                .filter(this::isPromotionCurrentlyActive)
                .collect(Collectors.toList());
    }

    /** A platform-wide promotion applies everywhere; a hotel promotion only to its own hotel. */
    public boolean appliesToHotel(Promotion promo, Long hotelId) {
        return promo.getHotel() == null || (hotelId != null && hotelId.equals(promo.getHotel().getId()));
    }

    /** The active COUPON with this code that can be used at this hotel, or null. */
    public Promotion findValidCoupon(String code, Long hotelId) {
        if (code == null || code.isBlank()) return null;
        String wanted = code.trim();
        return promotionRepository.findAll().stream()
                .filter(p -> p.getType() == Promotion.PromotionType.COUPON)
                .filter(p -> p.getCode() != null && p.getCode().trim().equalsIgnoreCase(wanted))
                .filter(this::isPromotionCurrentlyActive)
                .filter(p -> appliesToHotel(p, hotelId))
                .max(java.util.Comparator.comparing(p -> p.getDiscountPercentage() == null ? 0.0 : p.getDiscountPercentage()))
                .orElse(null);
    }

    /** Standard discounts currently valid for this hotel (platform-wide ones included). */
    public List<Promotion> getActiveDiscountsForHotel(Long hotelId) {
        return getActiveDiscountsForUser().stream()
                .filter(p -> appliesToHotel(p, hotelId))
                .collect(Collectors.toList());
    }

    // Standard (non-coupon) discounts currently valid - offered as a selectable option at booking time.
    public List<Promotion> getActiveDiscountsForUser() {
        return promotionRepository.findAll().stream()
                .filter(p -> p.getType() == Promotion.PromotionType.DISCOUNT)
                .filter(this::isPromotionCurrentlyActive)
                .collect(Collectors.toList());
    }
}