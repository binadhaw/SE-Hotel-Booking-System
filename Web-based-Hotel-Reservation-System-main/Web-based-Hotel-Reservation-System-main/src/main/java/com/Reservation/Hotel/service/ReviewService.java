package com.Reservation.Hotel.service;

import com.Reservation.Hotel.events.ReviewEvent;
import com.Reservation.Hotel.model.Booking;
import com.Reservation.Hotel.model.Review;
import com.Reservation.Hotel.repository.ReviewRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PBI-08: guests review a hotel after a completed stay (one review per booking).
 * PBI-11: anyone can read the reviews and average rating.
 * PBI-20: the hotel's manager replies publicly.
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ApplicationEventPublisher events;

    public ReviewService(ReviewRepository reviewRepository, ApplicationEventPublisher events) {
        this.reviewRepository = reviewRepository;
        this.events = events;
    }

    /** A stay is complete when an approved booking's check-out date has arrived, or the guest checked out early. */
    public boolean isStayCompleted(Booking booking) {
        if (booking == null || !"APPROVED".equals(booking.getStatus())) return false;
        boolean checkedIn = booking.getCheckInDate() == null || !booking.getCheckInDate().isAfter(LocalDate.now());
        return (booking.isRoomReleased() && checkedIn)
                || (booking.getCheckOutDate() != null && !booking.getCheckOutDate().isAfter(LocalDate.now()));
    }

    /** Days after check-out during which a stay can be reviewed. */
    public static final int REVIEW_WINDOW_DAYS = 30;

    /** The day the stay ended: the check-out date, or the day the guest left early. */
    public static LocalDate stayEnded(Booking booking) {
        LocalDate end = booking.getCheckOutDate();
        if (booking.isRoomReleased() && booking.getRoomReleasedAt() != null) {
            LocalDate left = booking.getRoomReleasedAt().toLocalDate();
            if (end == null || left.isBefore(end)) end = left;
        }
        return end;
    }

    /** Last day a review can be written for this stay (null when the stay has no end date). */
    public static LocalDate reviewDeadline(Booking booking) {
        LocalDate end = stayEnded(booking);
        return end == null ? null : end.plusDays(REVIEW_WINDOW_DAYS);
    }

    /** @return null when the user may review this booking, otherwise why not */
    public String reviewProblem(Booking booking, String username) {
        if (booking == null) return "Booking not found.";
        if (!booking.getUsername().equals(username)) return "You can only review your own stays.";
        if (!isStayCompleted(booking)) return "You can review the hotel once your stay is completed.";
        if (reviewRepository.existsByBookingId(booking.getId())) return "You have already reviewed this stay.";
        LocalDate deadline = reviewDeadline(booking);
        if (deadline != null && LocalDate.now().isAfter(deadline)) {
            return "Reviews can be written up to " + REVIEW_WINDOW_DAYS + " days after check-out - the window for this stay closed on "
                    + deadline.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")) + ".";
        }
        return null;
    }

    /** @return null when the guest may still edit or delete this review, otherwise why not */
    public String editProblem(Review review) {
        if (review.getManagerReply() != null) return "The hotel has already replied to this review, so it can no longer be changed.";
        if (!review.isEditable()) return "Reviews can only be edited or deleted within " + Review.EDIT_WINDOW_DAYS + " days of posting.";
        return null;
    }

    public boolean hasReview(Booking booking) {
        return booking != null && reviewRepository.existsByBookingId(booking.getId());
    }

    public boolean canReview(Booking booking, String username) {
        return reviewProblem(booking, username) == null;
    }

    @Transactional
    public Review create(Booking booking, String username, int rating, String title, String comment) {
        Review review = new Review();
        review.setHotel(booking.getHotel());
        review.setBooking(booking);
        review.setUsername(username);
        review.setRating(Math.max(1, Math.min(5, rating)));
        review.setTitle(clean(title, 120));
        review.setComment(clean(comment, 2000));
        Review saved = reviewRepository.save(review);
        events.publishEvent(new ReviewEvent(saved, ReviewEvent.Type.POSTED, username));
        return saved;
    }

    /** The guest edits their own review; the manager's reply stays. */
    @Transactional
    public void update(Review review, int rating, String title, String comment) {
        review.setRating(Math.max(1, Math.min(5, rating)));
        review.setTitle(clean(title, 120));
        review.setComment(clean(comment, 2000));
        reviewRepository.save(review);
    }

    @Transactional
    public void delete(Review review) {
        reviewRepository.delete(review);
    }

    public Review getById(Long id) {
        return reviewRepository.findById(id).orElse(null);
    }

    /** A few recent 4-5 star reviews with text, shown on the home page. */
    public List<Review> highlights() {
        return reviewRepository.findTop3ByHiddenFalseAndRatingGreaterThanEqualAndCommentIsNotNullOrderByCreatedAtDesc(4);
    }

    public List<Review> visibleForHotel(Long hotelId) {
        return reviewRepository.findByHotelIdAndHiddenFalseOrderByCreatedAtDesc(hotelId);
    }

    public List<Review> allForHotel(Long hotelId) {
        return reviewRepository.findByHotelIdOrderByCreatedAtDesc(hotelId);
    }

    @Transactional
    public void reply(Review review, String reply, String actor) {
        review.setManagerReply(clean(reply, 1000));
        review.setRepliedAt(review.getManagerReply() == null ? null : LocalDateTime.now());
        reviewRepository.save(review);
        if (review.getManagerReply() != null) {
            events.publishEvent(new ReviewEvent(review, ReviewEvent.Type.REPLIED, actor));
        }
    }

    @Transactional
    public void setHidden(Review review, boolean hidden, String actor) {
        review.setHidden(hidden);
        reviewRepository.save(review);
        events.publishEvent(new ReviewEvent(review, hidden ? ReviewEvent.Type.HIDDEN : ReviewEvent.Type.RESTORED, actor));
    }

    /** Counts of 5..1 star reviews (index 0 = 5 stars) for the rating bar chart. */
    public long[] distribution(List<Review> reviews) {
        long[] d = new long[5];
        for (Review r : reviews) d[5 - r.getRating()]++;
        return d;
    }

    private static String clean(String text, int max) {
        if (text == null || text.isBlank()) return null;
        String t = text.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
