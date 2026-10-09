package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.model.Booking;
import com.Reservation.Hotel.model.Review;
import com.Reservation.Hotel.service.BookingService;
import com.Reservation.Hotel.service.HotelService;
import com.Reservation.Hotel.service.ReviewService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final BookingService bookingService;
    private final HotelService hotelService;


    public ReviewController(ReviewService reviewService, BookingService bookingService, HotelService hotelService) {
        this.reviewService = reviewService;
        this.bookingService = bookingService;
        this.hotelService = hotelService;
    }

    @GetMapping("/new")
    public String showForm(@RequestParam("bookingId") Long bookingId, Authentication authentication, Model model,
                           RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.getBookingById(bookingId);
        String problem = reviewService.reviewProblem(booking, authentication.getName());
        if (problem != null) {
            redirectAttributes.addFlashAttribute("errorMessage", problem);
            return "redirect:/bookings";
        }
        model.addAttribute("booking", booking);
        return "reviews/create";
    }

    @PostMapping
    public String create(@RequestParam("bookingId") Long bookingId,
                         @RequestParam("rating") int rating,
                         @RequestParam(value = "title", required = false) String title,
                         @RequestParam(value = "comment", required = false) String comment,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.getBookingById(bookingId);
        String problem = reviewService.reviewProblem(booking, authentication.getName());
        if (problem == null && (rating < 1 || rating > 5)) problem = "Please choose a rating from 1 to 5 stars.";
        if (problem != null) {
            redirectAttributes.addFlashAttribute("errorMessage", problem);
            return "redirect:/bookings";
        }
        reviewService.create(booking, authentication.getName(), rating, title, comment);
        redirectAttributes.addFlashAttribute("successMessage", "Thank you! Your review has been published.");
        return "redirect:/hotels/" + booking.getHotel().getId() + "#reviews";
    }

    // ---------------- guest edits / deletes their own review ----------------

    private Review ownReview(Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        Review review = reviewService.getById(id);
        if (review == null || !review.getUsername().equals(authentication.getName())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can only change your own reviews.");
            return null;
        }
        String locked = reviewService.editProblem(review);
        if (locked != null) {
            redirectAttributes.addFlashAttribute("errorMessage", locked);
            return null;
        }
        return review;
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Authentication authentication, Model model,
                               RedirectAttributes redirectAttributes) {
        Review review = ownReview(id, authentication, redirectAttributes);
        if (review == null) return "redirect:/bookings";
        model.addAttribute("review", review);
        model.addAttribute("booking", review.getBooking());
        return "reviews/create";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id,
                         @RequestParam("rating") int rating,
                         @RequestParam(value = "title", required = false) String title,
                         @RequestParam(value = "comment", required = false) String comment,
                         Authentication authentication, RedirectAttributes redirectAttributes) {
        Review review = ownReview(id, authentication, redirectAttributes);
        if (review == null) return "redirect:/bookings";
        if (rating < 1 || rating > 5) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please choose a rating from 1 to 5 stars.");
            return "redirect:/reviews/" + id + "/edit";
        }
        reviewService.update(review, rating, title, comment);
        redirectAttributes.addFlashAttribute("successMessage", "Your review was updated.");
        return "redirect:/hotels/" + review.getHotel().getId() + "#reviews";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        Review review = ownReview(id, authentication, redirectAttributes);
        if (review == null) return "redirect:/bookings";
        Long hotelId = review.getHotel().getId();
        reviewService.delete(review);
        redirectAttributes.addFlashAttribute("successMessage", "Your review was deleted.");
        return "redirect:/hotels/" + hotelId + "#reviews";
    }

    /** PBI-20: the hotel's own manager replies publicly. */
    @PostMapping("/{id}/reply")
    public String reply(@PathVariable Long id, @RequestParam("reply") String reply,
                        Authentication authentication, RedirectAttributes redirectAttributes) {
        Review review = reviewService.getById(id);
        if (review == null || !hotelService.isOwner(review.getHotel(), authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only the hotel's manager can reply to its reviews.");
            return "redirect:/hotels";
        }
        reviewService.reply(review, reply, authentication.getName());
        redirectAttributes.addFlashAttribute("successMessage", "Reply published and the guest has been notified.");
        return "redirect:/hotels/" + review.getHotel().getId() + "#reviews";
    }

    /** Administrators can hide inappropriate reviews (and restore them). */
    @PostMapping("/{id}/visibility")
    public String visibility(@PathVariable Long id, @RequestParam("hidden") boolean hidden,
                             Authentication authentication, RedirectAttributes redirectAttributes) {
        Review review = reviewService.getById(id);
        if (review == null || !HotelService.hasRole(authentication, "ADMIN")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only administrators can moderate reviews.");
            return "redirect:/hotels";
        }
        reviewService.setHidden(review, hidden, authentication.getName());
        redirectAttributes.addFlashAttribute("successMessage", hidden ? "Review hidden." : "Review restored.");
        return "redirect:/hotels/" + review.getHotel().getId() + "#reviews";
    }
}
