package com.Reservation.Hotel;

import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.repository.HotelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs the whole application against an in-memory H2 database (see src/test/resources). */
@SpringBootTest
@AutoConfigureMockMvc
class WebFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired HotelRepository hotelRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired AppUserRepository userRepository;
    @Autowired RoomService roomService;
    @Autowired ReviewRepository reviewRepository;

    // ---------------- access rules ----------------

    @Test
    void visitorsCanBrowseButNotBook() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/hotels")).andExpect(status().isOk());
        mvc.perform(get("/bookings")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/hotels/new")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void touristCannotReachAdminOrManagerPages() throws Exception {
        mvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/users")).andExpect(status().isForbidden());
        mvc.perform(get("/hotels/new")).andExpect(status().isForbidden());
        mvc.perform(get("/reports/bookings.csv")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminSeesDashboard() throws Exception {
        mvc.perform(get("/admin/dashboard")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("System health")));
    }

    @Test
    void demoAccountsAreSeeded() {
        for (String u : new String[]{"admin", "manager", "agent", "user"}) {
            assertTrue(userRepository.existsByUsernameIgnoreCase(u), u + " exists");
        }
    }

    // ---------------- registration ----------------

    @Test
    void registrationCreatesAccountWithHashedPassword() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("role", "USER").param("fullName", "Jane Traveller").param("username", "jane")
                        .param("email", "jane@example.com").param("phone", "")
                        .param("password", "travel123").param("confirmPassword", "travel123")
                        .param("securityQuestion", "In which city were you born?").param("securityAnswer", "Galle")
                        .param("acceptPrivacy", "true"))
                .andExpect(redirectedUrl("/login"));
        var jane = userRepository.findByUsernameIgnoreCase("jane").orElseThrow();
        assertNotEquals("travel123", jane.getPassword(), "password is hashed");
        assertEquals("USER", jane.getRole());
        assertFalse(jane.isPromoAlerts(), "promotional emails are opt-in");
    }

    @Test
    void registrationNeedsPrivacyConsent() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("role", "USER").param("fullName", "No Consent").param("username", "noconsent")
                        .param("email", "nc@example.com")
                        .param("password", "travel123").param("confirmPassword", "travel123")
                        .param("securityQuestion", "In which city were you born?").param("securityAnswer", "x"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("accept the privacy notice")));
        assertFalse(userRepository.existsByUsernameIgnoreCase("noconsent"));

        mvc.perform(post("/register").with(csrf())
                        .param("role", "USER").param("fullName", "Deal Lover").param("username", "deals")
                        .param("email", "deals@example.com")
                        .param("password", "travel123").param("confirmPassword", "travel123")
                        .param("securityQuestion", "In which city were you born?").param("securityAnswer", "x")
                        .param("acceptPrivacy", "true").param("promoAlerts", "true"))
                .andExpect(redirectedUrl("/login"));
        assertTrue(userRepository.findByUsernameIgnoreCase("deals").orElseThrow().isPromoAlerts());

        mvc.perform(get("/privacy")).andExpect(status().isOk());
    }

    @Test
    void registrationCannotCreateAdmins() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("role", "ADMIN").param("fullName", "Mallory").param("username", "mallory")
                        .param("email", "m@example.com")
                        .param("password", "travel123").param("confirmPassword", "travel123")
                        .param("securityQuestion", "In which city were you born?").param("securityAnswer", "x")
                        .param("acceptPrivacy", "true"))
                .andExpect(status().isOk());   // form shown again with an error
        assertFalse(userRepository.existsByUsernameIgnoreCase("mallory"));
    }

    // ---------------- date-based availability (PBI-06) ----------------

    @Test
    void roomIsOnlyBlockedForOverlappingNights() {
        Hotel hotel = new Hotel("Overlap Inn", "Galle", Hotel.APPROVED, "test", "Free WiFi");
        hotel.setManagerUsername("manager");
        hotelRepository.save(hotel);
        Room room = new Room("1", "Deluxe", 100.0, true, hotel);
        roomRepository.save(room);

        LocalDate in = LocalDate.now().plusDays(10), out = in.plusDays(3);
        Booking b = new Booking();
        b.setHotel(hotel);
        b.setRoom(room);
        b.setUsername("user");
        b.setCheckInDate(in);
        b.setCheckOutDate(out);
        b.setStatus("APPROVED");
        bookingRepository.save(b);

        assertFalse(roomService.isRoomFree(room.getId(), in, out, null), "same dates");
        assertFalse(roomService.isRoomFree(room.getId(), in.plusDays(1), out.plusDays(1), null), "partial overlap");
        assertTrue(roomService.isRoomFree(room.getId(), out, out.plusDays(2), null), "starts on check-out day");
        assertTrue(roomService.isRoomFree(room.getId(), in.minusDays(2), in, null), "ends on check-in day");
        assertTrue(roomService.isRoomFree(room.getId(), in, out, b.getId()), "a booking does not clash with itself");

        b.setStatus("PENDING");
        bookingRepository.save(b);
        assertTrue(roomService.isRoomFree(room.getId(), in, out, null), "pending requests do not block the room");
    }

    // ---------------- booking page: live availability, default dates, no internal role names ----------------

    @Test
    void bookingPageDefaultsToOneNightAndReportsLiveAvailability() throws Exception {
        Hotel hotel = new Hotel("Live Avail Hotel", "Kandy", Hotel.APPROVED, "test", "Free WiFi");
        hotel.setManagerUsername("manager");
        hotelRepository.save(hotel);
        Room free = roomRepository.save(new Room("A1", "Deluxe", 80.0, true, hotel));
        Room taken = roomRepository.save(new Room("A2", "Suite", 150.0, true, hotel));
        LocalDate in = LocalDate.now().plusDays(20), out = in.plusDays(2);
        Booking b = new Booking();
        b.setHotel(hotel);
        b.setRoom(taken);
        b.setUsername("someone");
        b.setCheckInDate(in);
        b.setCheckOutDate(out);
        b.setStatus("APPROVED");
        bookingRepository.save(b);

        var tourist = user("user").roles("USER");
        // No dates in the link -> tonight to tomorrow, never a zero-night stay
        mvc.perform(get("/bookings/new/" + hotel.getId()).with(tourist)).andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"" + LocalDate.now() + "\"")))
                .andExpect(content().string(containsString("value=\"" + LocalDate.now().plusDays(1) + "\"")));

        String base = "/bookings/new/" + hotel.getId() + "/availability";
        mvc.perform(get(base).with(tourist).param("checkIn", in.toString()).param("checkOut", out.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.nights").value(2))
                .andExpect(jsonPath("$.freeRoomIds", org.hamcrest.Matchers.contains(free.getId().intValue())));
        mvc.perform(get(base).with(tourist).param("checkIn", in.toString()).param("checkOut", in.toString()))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message", containsString("one night")));
        mvc.perform(get(base).with(tourist).param("checkIn", in.toString()).param("checkOut", in.plusDays(31).toString()))
                .andExpect(jsonPath("$.message", containsString("30 nights")));
    }

    @Test
    void bookingErrorsKeepTheGuestsChoices() throws Exception {
        Hotel hotel = new Hotel("Keep State Inn", "Galle", Hotel.APPROVED, "test", "Free WiFi");
        hotel.setManagerUsername("manager");
        hotelRepository.save(hotel);
        Room room = roomRepository.save(new Room("K1", "Deluxe", 80.0, true, hotel));
        LocalDate in = LocalDate.now().plusDays(3);
        mvc.perform(post("/bookings").with(csrf()).with(user("user").roles("USER"))
                        .param("hotelId", hotel.getId().toString()).param("roomIds", room.getId().toString())
                        .param("checkInDate", in.toString()).param("checkOutDate", in.plusDays(40).toString())
                        .param("adults", "1").param("children", "0").param("paymentMethod", "CARD"))
                .andExpect(redirectedUrl("/bookings/new/" + hotel.getId() + "?roomIds=" + room.getId() + "&checkIn=" + in + "&checkOut=" + in.plusDays(40)))
                .andExpect(flash().attribute("errorMessage", containsString("30 nights")));
    }

    // ---------------- hosted payment page (redirect flow) ----------------

    private Booking payableBooking(String username, String roomNo) {
        Hotel hotel = new Hotel("Gateway Lodge " + roomNo, "Ella", Hotel.APPROVED, "test", "Free WiFi");
        hotel.setManagerUsername("manager");
        hotelRepository.save(hotel);
        Room room = roomRepository.save(new Room(roomNo, "Deluxe", 100.0, true, hotel));
        Booking b = new Booking();
        b.setHotel(hotel);
        b.setRoom(room);
        b.setUsername(username);
        b.setCheckInDate(LocalDate.now().plusDays(4));
        b.setCheckOutDate(LocalDate.now().plusDays(5));
        b.setStatus("PENDING");
        b.setFinalAmount(100.0);
        return bookingRepository.save(b);
    }

    /** Review page -> POST start -> returns the gateway session id from the redirect. */
    private String startGateway(Booking b, org.springframework.test.web.servlet.request.RequestPostProcessor who) throws Exception {
        mvc.perform(get("/payments/checkout").with(who).param("bookingIds", b.getId().toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Continue to secure payment")));
        String location = mvc.perform(post("/payments/checkout/start").with(csrf()).with(who).param("bookingIds", b.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertTrue(location.matches("/gateway/[a-f0-9]{32}"), location);
        return location.substring("/gateway/".length());
    }

    private static String demoCode(String html) {
        var m = java.util.regex.Pattern.compile("gw-code\">(\\d{6})<").matcher(html);
        assertTrue(m.find(), "the demo gateway shows the one-time code");
        return m.group(1);
    }

    @Test
    void hostedGatewayRedirectFlowConfirmsTheBooking() throws Exception {
        Booking b = payableBooking("payer", "G1");
        var payer = user("payer").roles("USER");
        String id = startGateway(b, payer);

        mvc.perform(get("/gateway/" + id).with(payer)).andExpect(status().isOk())
                .andExpect(content().string(containsString("AuraPay")))
                .andExpect(content().string(containsString("100.00")));

        // Invalid card: the number field is marked, the name is kept, the number is never echoed back
        mvc.perform(post("/gateway/" + id + "/card").with(csrf()).with(payer)
                        .param("cardHolder", "Pat Payer").param("cardNumber", "4242 4242 4242 4241")
                        .param("expiry", "12/30").param("cvv", "123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("not valid")))
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern("(?s).*id=\"cardNumber\"[^>]*is-invalid.*")))
                .andExpect(content().string(containsString("value=\"Pat Payer\"")))
                .andExpect(content().string(not(containsString("4242424242424241"))));

        // Valid card -> one-time code step
        mvc.perform(post("/gateway/" + id + "/card").with(csrf()).with(payer)
                        .param("cardHolder", "Pat Payer").param("cardNumber", "4242 4242 4242 4242")
                        .param("expiry", "12/30").param("cvv", "123"))
                .andExpect(redirectedUrl("/gateway/" + id));
        String html = mvc.perform(get("/gateway/" + id).with(payer)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Confirm it's you")))
                .andReturn().getResponse().getContentAsString();
        String code = demoCode(html);
        assertEquals("PENDING", bookingRepository.findById(b.getId()).orElseThrow().getStatus(), "nothing charged before the code");

        mvc.perform(post("/gateway/" + id + "/otp").with(csrf()).with(payer).param("code", code.equals("000000") ? "111111" : "000000"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("2 tries left")));

        // Right code -> charged -> back to the hotel site -> receipt
        mvc.perform(post("/gateway/" + id + "/otp").with(csrf()).with(payer).param("code", code))
                .andExpect(redirectedUrl("/payments/return/" + id));
        String receipt = mvc.perform(get("/payments/return/" + id).with(payer))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertTrue(receipt.matches("/payments/TXN-[A-Z0-9]+"), receipt);
        assertEquals("APPROVED", bookingRepository.findById(b.getId()).orElseThrow().getStatus());
        mvc.perform(get(receipt).with(payer)).andExpect(status().isOk()).andExpect(content().string(containsString("Payment successful")));

        // A finished session cannot be paid again
        mvc.perform(get("/gateway/" + id).with(payer)).andExpect(redirectedUrl("/payments/return/" + id));
    }

    @Test
    void gatewaySessionsArePrivateAndCanBeCancelled() throws Exception {
        Booking b = payableBooking("owner1", "G2");
        var owner = user("owner1").roles("USER");
        String id = startGateway(b, owner);

        // Someone else's session link shows "not found" and cannot be used
        mvc.perform(get("/gateway/" + id).with(user("intruder").roles("USER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Payment not found")));
        mvc.perform(post("/gateway/" + id + "/card").with(csrf()).with(user("intruder").roles("USER"))
                        .param("cardHolder", "Eve Evil").param("cardNumber", "4242424242424242").param("expiry", "12/30").param("cvv", "123"))
                .andExpect(content().string(containsString("Payment not found")));
        // Managers cannot use the gateway at all
        mvc.perform(get("/gateway/" + id).with(user("manager").roles("MANAGER"))).andExpect(status().isForbidden());

        // Cancel -> back to the review page with a message, nothing charged
        mvc.perform(post("/gateway/" + id + "/cancel").with(csrf()).with(owner)).andExpect(redirectedUrl("/payments/return/" + id));
        mvc.perform(get("/payments/return/" + id).with(owner))
                .andExpect(redirectedUrl("/payments/checkout?bookingIds=" + b.getId()))
                .andExpect(flash().attribute("errorMessage", containsString("cancelled")));
        assertEquals("PENDING", bookingRepository.findById(b.getId()).orElseThrow().getStatus());
    }

    @Test
    void gatewayLocksAfterRepeatedFailures() throws Exception {
        Booking b = payableBooking("locked", "G3");
        var who = user("locked").roles("USER");
        String id = startGateway(b, who);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/gateway/" + id + "/card").with(csrf()).with(who)
                    .param("cardHolder", "Lock Tester").param("cardNumber", "4242424242424241").param("expiry", "12/30").param("cvv", "123"));
        }
        mvc.perform(post("/gateway/" + id + "/card").with(csrf()).with(who)
                        .param("cardHolder", "Lock Tester").param("cardNumber", "4242424242424242").param("expiry", "12/30").param("cvv", "123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Too many unsuccessful payment attempts")));
        assertEquals("PENDING", bookingRepository.findById(b.getId()).orElseThrow().getStatus(), "not charged while locked");
    }

    @Test
    void declinedCardReturnsToReviewWithTheBankMessage() throws Exception {
        Booking b = payableBooking("decliner", "G4");
        var who = user("decliner").roles("USER");
        String id = startGateway(b, who);
        mvc.perform(post("/gateway/" + id + "/card").with(csrf()).with(who)
                .param("cardHolder", "Dee Cline").param("cardNumber", "4000 0000 0000 0002").param("expiry", "12/30").param("cvv", "123"));
        String code = demoCode(mvc.perform(get("/gateway/" + id).with(who)).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/gateway/" + id + "/otp").with(csrf()).with(who).param("code", code))
                .andExpect(redirectedUrl("/payments/return/" + id));
        mvc.perform(get("/payments/return/" + id).with(who))
                .andExpect(redirectedUrl("/payments/checkout?bookingIds=" + b.getId()))
                .andExpect(flash().attribute("errorMessage", containsString("declined")));
        assertEquals("PENDING", bookingRepository.findById(b.getId()).orElseThrow().getStatus());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void hotelDirectoryShowsFriendlyRoleNotInternalTag() throws Exception {
        mvc.perform(get("/hotels")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("ROLE_"))))
                .andExpect(content().string(containsString("Administrator")));
    }

    // ---------------- payment receipts are private ----------------

    @Test
    void receiptsAreOnlyVisibleToGuestHotelManagerAndAdmin() throws Exception {
        Hotel hotel = new Hotel("Receipt Resort", "Galle", Hotel.APPROVED, "test", "Free WiFi");
        hotel.setManagerUsername("manager");
        hotelRepository.save(hotel);
        Room room = new Room("R1", "Deluxe", 100.0, true, hotel);
        roomRepository.save(room);

        java.nio.file.Path dir = java.nio.file.Paths.get("uploads", "receipts");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Path file = dir.resolve("test-" + java.util.UUID.randomUUID() + ".pdf");
        java.nio.file.Files.writeString(file, "%PDF-1.4 test receipt");
        try {
            Booking b = new Booking();
            b.setHotel(hotel);
            b.setRoom(room);
            b.setUsername("user");
            b.setCheckInDate(LocalDate.now().plusDays(5));
            b.setCheckOutDate(LocalDate.now().plusDays(6));
            b.setStatus("PENDING");
            b.setReceiptImagePath("/uploads/receipts/" + file.getFileName());
            bookingRepository.save(b);
            String url = "/bookings/" + b.getId() + "/receipt-file";

            mvc.perform(get(url).with(user("user").roles("USER"))).andExpect(status().isOk())
                    .andExpect(content().contentType("application/pdf"));
            mvc.perform(get(url).with(user("manager").roles("MANAGER"))).andExpect(status().isOk());
            mvc.perform(get(url).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());

            mvc.perform(get(url).with(user("someoneelse").roles("USER"))).andExpect(status().isForbidden());
            mvc.perform(get(url).with(user("othermanager").roles("MANAGER"))).andExpect(status().isForbidden());
            mvc.perform(get(url)).andExpect(status().is3xxRedirection()); // not logged in -> login page

            // The old direct URL no longer works for anyone
            mvc.perform(get(b.getReceiptImagePath()).with(user("user").roles("USER"))).andExpect(status().isForbidden());
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    // ---------------- reviews (T-14.2: paginate reviews per hotel) ----------------

    @Test
    void hotelReviewsArePaginated() throws Exception {
        Hotel hotel = new Hotel("Review Lodge", "Ella", Hotel.APPROVED, "test", "Free WiFi");
        hotel.setManagerUsername("manager");
        hotelRepository.save(hotel);
        for (int i = 1; i <= 7; i++) {
            Review r = new Review();
            r.setHotel(hotel);
            r.setUsername("user");
            r.setRating(5);
            r.setTitle("Stay number " + i);
            r.setCreatedAt(LocalDateTime.now().minusDays(10 - i)); // newest = 7
            reviewRepository.save(r);
        }

        mvc.perform(get("/hotels/" + hotel.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Stay number 7")))
                .andExpect(content().string(containsString("Stay number 3")))
                .andExpect(content().string(not(containsString("Stay number 2"))))
                .andExpect(content().string(containsString("reviewPage=2")))
                .andExpect(content().string(containsString("7 verified reviews")));

        mvc.perform(get("/hotels/" + hotel.getId()).param("reviewPage", "2")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Stay number 1")))
                .andExpect(content().string(not(containsString("Stay number 7"))));

        // Out-of-range pages are clamped instead of failing
        mvc.perform(get("/hotels/" + hotel.getId()).param("reviewPage", "99")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Stay number 1")));
    }
}
