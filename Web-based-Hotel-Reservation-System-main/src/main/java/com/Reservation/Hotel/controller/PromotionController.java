package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.model.Promotion;
import com.Reservation.Hotel.service.HotelService;
import com.Reservation.Hotel.service.PromotionService;
import org.springframework.security.core.Authentication;
import com.Reservation.Hotel.service.ActivityLogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Promotions (PBI-19): a hotel manager runs offers for their own hotels; an administrator can run
 * platform-wide offers or offers for any hotel. Coupons can be emailed to opted-in tourists (PBI-04).
 */
@Controller
@RequestMapping("/promotions")
public class PromotionController {

    private final PromotionService promotionService;
    private final HotelService hotelService;

    private final ActivityLogService activityLog;

    public PromotionController(PromotionService promotionService, HotelService hotelService, ActivityLogService activityLog) {
        this.activityLog = activityLog;
        this.promotionService = promotionService;
        this.hotelService = hotelService;
    }

    private boolean isAdmin(Authentication authentication) {
        return HotelService.hasRole(authentication, "ADMIN");
    }

    /** Admins manage every promotion; a manager only the ones for hotels they own. */
    private boolean canManage(Promotion promo, Authentication authentication) {
        if (promo == null) return false;
        if (isAdmin(authentication)) return true;
        return promo.getHotel() != null && hotelService.isOwner(promo.getHotel(), authentication);
    }

    private List<Hotel> scopeHotels(Authentication authentication) {
        return isAdmin(authentication)
                ? hotelService.filterAndSearchHotels(Hotel.APPROVED, null)
                : hotelService.filterManagerHotels(authentication.getName(), Hotel.APPROVED, null);
    }

    @GetMapping
    public String listPromotions(@RequestParam(value = "type", required = false, defaultValue = "ALL") String type,
                                 Authentication authentication, Model model) {
        List<Promotion> promotions = promotionService.getPromotionsByType(type);
        boolean staff = isAdmin(authentication) || HotelService.hasRole(authentication, "MANAGER");
        if (HotelService.hasRole(authentication, "MANAGER")) {
            // A manager sees their own hotels' offers plus the platform-wide ones
            promotions = promotions.stream()
                    .filter(p -> p.getHotel() == null || hotelService.isOwner(p.getHotel(), authentication))
                    .collect(Collectors.toList());
        } else if (!staff) {
            // Tourists see offers that are running now; coupon codes are only revealed while active
            promotions = promotions.stream().filter(promotionService::isPromotionCurrentlyActive).collect(Collectors.toList());
        }
        model.addAttribute("promotions", promotions);
        List<Promotion> activeCoupons = promotionService.getActiveCouponsForUser();
        model.addAttribute("activeCoupons", activeCoupons);
        addTimingInfo(model, promotions, activeCoupons);
        model.addAttribute("currentFilter", type);
        model.addAttribute("editableIds", promotions.stream().filter(p -> canManage(p, authentication))
                .map(Promotion::getId).collect(Collectors.toSet()));
        return "promotions/index";
    }

    /**
     * Per offer: its real state (live, between flash windows, scheduled, expired, switched off), when the live
     * window ends (epoch millis, for the on-page countdown) and when the next flash window opens.
     */
    private void addTimingInfo(Model model, List<Promotion> promotions, List<Promotion> activeCoupons) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.util.Map<Long, String> states = new java.util.HashMap<>();
        java.util.Map<Long, Long> endsAt = new java.util.HashMap<>();
        java.util.Map<Long, java.time.LocalDateTime> nextStart = new java.util.HashMap<>();
        java.util.stream.Stream.concat(promotions.stream(), activeCoupons.stream()).distinct().forEach(p -> {
            states.put(p.getId(), promotionService.stateOf(p, now).name());
            java.time.LocalDateTime until = promotionService.liveUntil(p, now);
            if (until != null) endsAt.put(p.getId(), until.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            java.time.LocalDateTime next = promotionService.nextWindowStart(p, now);
            if (next != null) nextStart.put(p.getId(), next);
        });
        model.addAttribute("promoStates", states);
        model.addAttribute("promoEndsAt", endsAt);
        model.addAttribute("promoNextStart", nextStart);
    }

    /** Live coupon check used by the booking form's price preview. */
    @GetMapping("/check-coupon")
    @ResponseBody
    public Map<String, Object> checkCoupon(@RequestParam("code") String code,
                                           @RequestParam(value = "hotelId", required = false) Long hotelId) {
        Promotion coupon = promotionService.findValidCoupon(code, hotelId);
        if (coupon == null) {
            return Map.of("valid", false, "message", "This coupon is not valid for this hotel right now.");
        }
        return Map.of("valid", true, "percent", coupon.getDiscountPercentage(),
                "message", coupon.getDiscountPercentage() + "% off - " + (coupon.getDescription() == null ? "" : coupon.getDescription()));
    }

    @GetMapping("/create-type")
    public String selectPromotionType() {
        return "promotions/create-type";
    }

    private String showForm(Promotion promotion, Authentication authentication, Model model) {
        model.addAttribute("promotion", promotion);
        model.addAttribute("scopeHotels", scopeHotels(authentication));
        model.addAttribute("isAdmin", isAdmin(authentication));
        return promotion.getType() == Promotion.PromotionType.DISCOUNT ? "promotions/create-discount" : "promotions/create-coupon";
    }

    @GetMapping("/new/coupon")
    public String showCreateCouponForm(Authentication authentication, Model model) {
        Promotion promotion = new Promotion();
        promotion.setType(Promotion.PromotionType.COUPON);
        return showForm(promotion, authentication, model);
    }

    @GetMapping("/new/discount")
    public String showCreateDiscountForm(Authentication authentication, Model model) {
        Promotion promotion = new Promotion();
        promotion.setType(Promotion.PromotionType.DISCOUNT);
        return showForm(promotion, authentication, model);
    }

    @PostMapping
    public String savePromotion(@ModelAttribute("promotion") Promotion form,
                                @RequestParam(value = "hotelId", required = false) Long hotelId,
                                Authentication authentication, Model model,
                                RedirectAttributes redirectAttributes) {
        Promotion promotion = new Promotion();
        promotion.setType(form.getType() == null ? Promotion.PromotionType.COUPON : form.getType());
        promotion.setCreatedBy(authentication.getName());
        return saveFromForm(promotion, form, hotelId, authentication, model, redirectAttributes, true);
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable("id") Long id, Authentication authentication, Model model,
                               RedirectAttributes redirectAttributes) {
        Promotion promotion = promotionService.getPromotionById(id).orElse(null);
        if (!canManage(promotion, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can only edit promotions for your own hotels.");
            return "redirect:/promotions";
        }
        return showForm(promotion, authentication, model);
    }

    @PostMapping("/{id}/update")
    public String updatePromotion(@PathVariable("id") Long id, @ModelAttribute("promotion") Promotion form,
                                  @RequestParam(value = "hotelId", required = false) Long hotelId,
                                  Authentication authentication, Model model,
                                  RedirectAttributes redirectAttributes) {
        Promotion promotion = promotionService.getPromotionById(id).orElse(null);
        if (!canManage(promotion, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can only edit promotions for your own hotels.");
            return "redirect:/promotions";
        }
        return saveFromForm(promotion, form, hotelId, authentication, model, redirectAttributes, false);
    }

    /** Copies only the editable fields from the form, validates them and saves. */
    private String saveFromForm(Promotion target, Promotion form, Long hotelId, Authentication authentication,
                                Model model, RedirectAttributes redirectAttributes, boolean isNew) {
        Hotel hotel = hotelService.getHotelById(hotelId);
        String problem = null;
        if (hotel != null && (!hotel.isApproved() || (!isAdmin(authentication) && !hotelService.isOwner(hotel, authentication)))) {
            problem = "You can only create offers for your own approved hotels.";
        } else if (hotel == null && !isAdmin(authentication)) {
            problem = "Choose which of your hotels the offer is for.";
        } else if (form.getDescription() == null || form.getDescription().isBlank() || form.getDescription().length() > 255) {
            problem = "Give the offer a title (up to 255 characters).";
        } else if (form.getTerms() != null && form.getTerms().length() > 500) {
            problem = "Terms must be 500 characters or fewer.";
        } else if (form.getDiscountPercentage() == null || form.getDiscountPercentage() < 1 || form.getDiscountPercentage() > 90) {
            problem = "The discount must be between 1% and 90%.";
        } else if (form.getValidFrom() != null && form.getValidUntil() != null && !form.getValidUntil().isAfter(form.getValidFrom())) {
            problem = "The end date must be after the start date.";
        } else if (form.getValidUntil() != null && !form.getValidUntil().equals(target.getValidUntil())
                && !form.getValidUntil().isAfter(java.time.LocalDateTime.now())) {
            problem = "The end date has already passed - choose a time in the future.";
        } else if (target.getType() == Promotion.PromotionType.COUPON && flashProblem(form) != null) {
            problem = flashProblem(form);
        } else if (target.getType() == Promotion.PromotionType.COUPON) {
            String code = form.getCode() == null ? "" : form.getCode().trim().toUpperCase();
            if (!code.matches("^[A-Z0-9_-]{3,30}$")) {
                problem = "Coupon codes must be 3-30 letters, numbers, dashes or underscores.";
            } else if (promotionService.couponCodeTaken(code, target.getId())) {
                problem = "Another coupon already uses the code " + code + ".";
            }
        }

        if (problem != null) {
            form.setId(target.getId());
            form.setType(target.getType());
            form.setHotel(hotel);
            model.addAttribute("errorMessage", problem);
            return showForm(form, authentication, model);
        }

        target.setHotel(hotel);
        target.setDescription(form.getDescription().trim());
        target.setDiscountPercentage(form.getDiscountPercentage());
        target.setActive(form.isActive());
        target.setTerms(form.getTerms());
        target.setValidUntil(form.getValidUntil());
        if (target.getType() == Promotion.PromotionType.COUPON) {
            target.setCode(form.getCode().trim().toUpperCase());
            target.setIntervalMinutes(form.getIntervalMinutes() != null && form.getIntervalMinutes() > 0 ? form.getIntervalMinutes() : null);
            target.setDurationMinutes(form.getDurationMinutes() != null && form.getDurationMinutes() > 0 ? form.getDurationMinutes() : null);
            if (isNew) target.setValidFrom(null);   // starts now
        } else {
            target.setCode(null);
            target.setValidFrom(form.getValidFrom());
        }
        promotionService.savePromotion(target);
        activityLog.log(authentication.getName(), isNew ? "PROMOTION_CREATED" : "PROMOTION_UPDATED",
                (target.getCode() != null ? target.getCode() + " " : "") + target.getDiscountPercentage() + "% - " + target.getScopeLabel());

        String msg = isNew ? "Promotion created successfully!" : "Promotion updated successfully!";
        if (isNew && target.isActive()) {
            int alerted = promotionService.alertSubscribers(target);
            if (alerted > 0) msg += " " + alerted + " tourist(s) with promo alerts were notified.";
        }
        redirectAttributes.addFlashAttribute("successMessage", msg);
        return "redirect:/promotions";
    }

    /** A flash coupon needs both a cycle and a shorter window, or neither (always valid). */
    private static String flashProblem(Promotion form) {
        boolean hasCycle = form.getIntervalMinutes() != null && form.getIntervalMinutes() > 0;
        boolean hasWindow = form.getDurationMinutes() != null && form.getDurationMinutes() > 0;
        if (hasCycle != hasWindow) return "Set both the flash window and how often it repeats, or leave both empty.";
        if (hasCycle && form.getDurationMinutes() >= form.getIntervalMinutes()) {
            return "The flash window must be shorter than the cycle (e.g. live 15 minutes of every 60).";
        }
        if (hasCycle && form.getIntervalMinutes() > 7 * 24 * 60) return "A flash cycle can be at most 7 days.";
        return null;
    }

    /** Emails the coupon (value, terms, expiry) to every tourist who opted in to promo alerts. */
    @PostMapping("/{id}/send")
    public String sendCoupon(@PathVariable("id") Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        Promotion promotion = promotionService.getPromotionById(id).orElse(null);
        if (!canManage(promotion, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can only send coupons for your own hotels.");
        } else if (promotion.getType() != Promotion.PromotionType.COUPON || !promotion.isActive()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only active coupons can be sent.");
        } else if (promotionService.isExpired(promotion)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Coupon " + promotion.getCode() + " has expired - extend its end date before sending it.");
        } else {
            int sent = promotionService.emailCoupon(promotion);
            activityLog.log(authentication.getName(), "PROMOTION_SENT", promotion.getCode() + " to " + sent + " tourist(s)");
            redirectAttributes.addFlashAttribute("successMessage", "Coupon " + promotion.getCode() + " sent to " + sent + " tourist(s).");
        }
        return "redirect:/promotions";
    }

    @PostMapping("/{id}/delete")
    public String deletePromotion(@PathVariable("id") Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        Promotion promotion = promotionService.getPromotionById(id).orElse(null);
        if (!canManage(promotion, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can only delete promotions for your own hotels.");
            return "redirect:/promotions";
        }
        promotionService.deletePromotion(id);
        activityLog.log(authentication.getName(), "PROMOTION_DELETED", "Promotion #" + id);
        redirectAttributes.addFlashAttribute("successMessage", "Promotion deleted successfully!");
        return "redirect:/promotions";
    }
}
