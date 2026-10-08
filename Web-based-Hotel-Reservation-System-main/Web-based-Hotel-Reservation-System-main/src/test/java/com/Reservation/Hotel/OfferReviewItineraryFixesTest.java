package com.Reservation.Hotel;

import com.Reservation.Hotel.model.*;
import com.Reservation.Hotel.repository.*;
import com.Reservation.Hotel.service.AgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Flash coupon deadlines, review time limits and the itinerary change-request flow. */
@SpringBootTest
@AutoConfigureMockMvc
class OfferReviewItineraryFixesTest {

    @Autowired MockMvc mvc;
    @Autowired PromotionService promotionService;
    @Autowired PromotionRepository promotionRepository;
    @Autowired ReviewService reviewService;
    @Autowired ReviewRepository reviewRepository;
    @Autowired HotelRepository hotelRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired AgentService agentService;
    @Autowired AgentProfileRepository agentProfileRepository;

    // ---------------- flash coupons ----------------

    private static Promotion coupon(LocalDateTime start, Integer every, Integer live, LocalDateTime until) {
        Promotion p = new Promotion();
        p.setType(Promotion.PromotionType.COUPON);
        p.setCode("FLASHX");
        p.setDiscountPercentage(20.0);
        p.setActive(true);
        p.setValidFrom(start);
        p.setIntervalMinutes(every);
        p.setDurationMinutes(live);
        p.setValidUntil(until);
        return p;
    }

    @Test
    void flashCouponStatesAndCountdownTargets() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 6, 10, 0);
        Promotion p = coupon(t, 60, 15, t.plusHours(5));

        assertEquals(PromotionService.PromoState.LIVE, promotionService.stateOf(p, t.plusMinutes(10)));
        assertEquals(t.plusMinutes(15), promotionService.liveUntil(p, t.plusMinutes(10)), "counts down to the end of the window");

        assertEquals(PromotionService.PromoState.BETWEEN_WINDOWS, promotionService.stateOf(p, t.plusMinutes(20)));
        assertEquals(t.plusMinutes(60), promotionService.nextWindowStart(p, t.plusMinutes(20)));

        // The end date cuts a window short, and no "next window" is promised after it
        Promotion ending = coupon(t, 60, 15, t.plusMinutes(70));
        assertEquals(t.plusMinutes(70), promotionService.liveUntil(ending, t.plusMinutes(65)));
        Promotion endsBeforeNext = coupon(t, 60, 15, t.plusMinutes(50));
        assertNull(promotionService.nextWindowStart(endsBeforeNext, t.plusMinutes(20)));

        assertEquals(PromotionService.PromoState.EXPIRED, promotionService.stateOf(p, t.plusHours(5)));
        assertFalse(promotionService.isPromotionCurrentlyActive(p, t.plusHours(5).plusMinutes(5)));
        p.setActive(false);
        assertEquals(PromotionService.PromoState.OFF, promotionService.stateOf(p, t.plusMinutes(10)));
    }

    @Test
    void couponFormRefusesPastDeadlinesAndAlwaysOnWindows() throws Exception {
        var admin = user("admin").roles("ADMIN");
        mvc.perform(post("/promotions").with(csrf()).with(admin)
                        .param("type", "COUPON").param("code", "PASTDEAL").param("description", "Old deal")
                        .param("discountPercentage", "10").param("active", "true")
                        .param("validUntil", LocalDateTime.now().minusHours(1).withNano(0).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already passed")));
        mvc.perform(post("/promotions").with(csrf()).with(admin)
                        .param("type", "COUPON").param("code", "ALWAYSON").param("description", "Broken flash")
                        .param("discountPercentage", "10").param("active", "true")
                        .param("intervalMinutes", "30").param("durationMinutes", "30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("shorter than the cycle")));
        assertTrue(promotionRepository.findAll().stream().noneMatch(p -> "PASTDEAL".equals(p.getCode()) || "ALWAYSON".equals(p.getCode())));
    }

    @Test
    void expiredCouponsAreNotSentOrListedAsLive() throws Exception {
        Promotion p = coupon(LocalDateTime.now().minusDays(3), null, null, LocalDateTime.now().minusMinutes(1));
        p.setCode("EXPIRED1");
        p.setDescription("Ended deal");
        promotionRepository.save(p);
        assertFalse(promotionService.getActiveCouponsForUser().stream().anyMatch(c -> "EXPIRED1".equals(c.getCode())));

        mvc.perform(post("/promotions/" + p.getId() + "/send").with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(flash().attribute("errorMessage", containsString("has expired")));
    }

    @Test
    void changedPagesRenderForEveryRole() throws Exception {
        Promotion live = coupon(LocalDateTime.now().minusMinutes(5), 60, 30, LocalDateTime.now().plusDays(1));
        live.setCode("LIVENOW");
        live.setDescription("Live flash deal");
        promotionRepository.save(live);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/promotions").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("data-ends-at")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/promotions").with(user("user").roles("USER")))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/promotions/new/coupon").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Coupon ends")));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/").with(user("user").roles("USER")))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/agents/explore").with(user("user").roles("USER")))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/agents/portal").with(user("agent").roles("AGENT")))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/bookings").with(user("user").roles("USER")))
                .andExpect(status().isOk());
    }

    // ---------------- reviews ----------------

    private Booking stay(String username, LocalDate checkOut) {
        Hotel hotel = new Hotel("Review Window Inn " + checkOut, "Galle", Hotel.APPROVED, "t", "Free WiFi");
        hotel.setManagerUsername("manager");
        hotelRepository.save(hotel);
        Room room = roomRepository.save(new Room("R" + checkOut.getDayOfYear(), "Deluxe", 80.0, true, hotel));
        Booking b = new Booking();
        b.setHotel(hotel);
        b.setRoom(room);
        b.setUsername(username);
        b.setCheckInDate(checkOut.minusDays(2));
        b.setCheckOutDate(checkOut);
        b.setStatus("APPROVED");
        return bookingRepository.save(b);
    }

    @Test
    void reviewsMustBeWrittenWithinThirtyDaysOfCheckOut() {
        Booking recent = stay("reviewer", LocalDate.now().minusDays(10));
        Booking old = stay("reviewer", LocalDate.now().minusDays(31));
        assertNull(reviewService.reviewProblem(recent, "reviewer"));
        assertTrue(reviewService.reviewProblem(old, "reviewer").contains("30 days"));
        assertFalse(reviewService.canReview(old, "reviewer"));
    }

    @Test
    void earlyCheckOutCountsAsAStayOnlyAfterCheckIn(@Autowired com.Reservation.Hotel.service.BookingService bookingService) {
        // Guest checked in today and left early: the manager releases the room, the guest can review
        Booking leftEarly = stay("earlybird", LocalDate.now().plusDays(2));
        leftEarly.setCheckInDate(LocalDate.now());
        bookingRepository.save(leftEarly);
        assertNull(bookingService.releaseRoom(leftEarly));
        assertTrue(reviewService.canReview(leftEarly, "earlybird"));

        // A stay that has not started cannot be "released", so it can never be reviewed early
        Booking future = stay("earlybird", LocalDate.now().plusDays(9));
        assertTrue(bookingService.releaseRoom(future).contains("not checked in"));
        future.setRoomReleased(true);   // even if the flag were set some other way
        assertFalse(reviewService.isStayCompleted(future));
    }

    @Test
    void reviewsLockAfterTheHotelRepliesOrAfterFourteenDays() throws Exception {
        var reviewer = user("reviewer2").roles("USER");
        Review replied = reviewService.create(stay("reviewer2", LocalDate.now().minusDays(3)), "reviewer2", 4, "Nice", "Good stay");
        reviewService.reply(replied, "Thank you!", "manager");
        mvc.perform(post("/reviews/" + replied.getId() + "/update").with(csrf()).with(reviewer)
                        .param("rating", "1").param("title", "Changed my mind").param("comment", "x"))
                .andExpect(flash().attribute("errorMessage", containsString("already replied")));
        assertEquals(4, reviewRepository.findById(replied.getId()).orElseThrow().getRating(), "rating unchanged");

        Review old = reviewService.create(stay("reviewer2", LocalDate.now().minusDays(5)), "reviewer2", 5, "Great", "Lovely");
        old.setCreatedAt(LocalDateTime.now().minusDays(Review.EDIT_WINDOW_DAYS + 1));
        reviewRepository.save(old);
        mvc.perform(post("/reviews/" + old.getId() + "/delete").with(csrf()).with(reviewer))
                .andExpect(flash().attribute("errorMessage", containsString("14 days")));
        assertTrue(reviewRepository.existsById(old.getId()), "not deleted");

        Review fresh = reviewService.create(stay("reviewer2", LocalDate.now().minusDays(1)), "reviewer2", 3, "OK", "Fine");
        assertTrue(fresh.isEditable());
        assertNull(reviewService.editProblem(fresh));
    }

    // ---------------- itinerary change requests ----------------

    @Test
    @org.springframework.transaction.annotation.Transactional   // reads the lazily loaded version history
    void itineraryChangesKeepTheirHistoryAndStaleDecisionsAreRefused() {
        AgentProfile agent = new AgentProfile();
        agent.setUsername("agentfix");
        agent.setAgencyName("Fix Tours");
        agent.setPhone("+94 77 555 0101");
        agent.setStatus("APPROVED");
        agentProfileRepository.save(agent);

        VacationRequest req = new VacationRequest();
        req.setUserName("Tess Traveller");
        req.setUserPreferences("Beaches and tea country");
        assertNull(agentService.createVacationRequest(agent.getId(), req, "tess"));

        assertNull(agentService.submitAgentProposal(req.getId(), "agentfix", "Day 1: Galle", 500, null, List.of()));
        assertNull(agentService.handleUserDecision(req.getId(), "tess", "RE_REQUEST", "Add a day in Ella", 1));

        // A new version must say what changed, and remembers the request it answers
        assertNotNull(agentService.submitAgentProposal(req.getId(), "agentfix", "Day 1: Galle\nDay 2: Ella", 650, " ", List.of()));
        assertNull(agentService.submitAgentProposal(req.getId(), "agentfix", "Day 1: Galle\nDay 2: Ella", 650, "Added Ella", List.of()));
        VacationRequest after = agentService.getVacationRequestById(req.getId());
        assertEquals(2, after.getCurrentRevision().getVersion());
        assertEquals("Add a day in Ella", after.getCurrentRevision().getRequestedChanges());
        assertEquals("Added Ella", after.getCurrentRevision().getChangeNote());

        // Accepting the version you saw (v1) after the agent sent v2 is refused
        String stale = agentService.handleUserDecision(req.getId(), "tess", "ACCEPT", null, 1);
        assertNotNull(stale);
        assertTrue(stale.contains("newer version"));
        assertEquals("PROPOSED", agentService.getVacationRequestById(req.getId()).getStatus());

        assertNull(agentService.handleUserDecision(req.getId(), "tess", "ACCEPT", null, 2));
        assertEquals("ACCEPTED", agentService.getVacationRequestById(req.getId()).getStatus());
    }
}
