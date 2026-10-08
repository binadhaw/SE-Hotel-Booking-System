package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.model.Booking;
import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.model.Room;
import com.Reservation.Hotel.service.BookingService;
import com.Reservation.Hotel.service.HotelService;
import com.Reservation.Hotel.service.PromotionService;
import com.Reservation.Hotel.service.ReviewService;
import com.Reservation.Hotel.service.RoomService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import com.Reservation.Hotel.events.BookingEvent;
import com.Reservation.Hotel.payment.PaymentService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final HotelService hotelService;
    private final RoomService roomService;
    private final PromotionService promotionService;
    private final ReviewService reviewService;
    private final PaymentService paymentService;
    private final com.Reservation.Hotel.service.AttractionService attractionService;

    private final ApplicationEventPublisher events;

    public BookingController(BookingService bookingService, HotelService hotelService,
                             RoomService roomService,
                             PromotionService promotionService,
                             ReviewService reviewService, ApplicationEventPublisher events,
                             PaymentService paymentService,
                             com.Reservation.Hotel.service.AttractionService attractionService) {
        this.attractionService = attractionService;
        this.paymentService = paymentService;
        this.events = events;
        this.reviewService = reviewService;
        this.bookingService = bookingService;
        this.hotelService = hotelService;
        this.roomService = roomService;
        this.promotionService = promotionService;
    }

    private boolean isManager(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));
    }

    private boolean isManagerOrAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Only the guest who made the booking may edit or cancel it. A manager can approve, reject,
     * release the room or delete a closed booking - but never change the guest's booking details.
     */
    private boolean canAccess(Booking booking, Authentication authentication) {
        return booking.getUsername() != null && booking.getUsername().equals(authentication.getName());
    }

    /** Manager actions are only allowed on bookings of hotels the manager owns. */
    private boolean notOwner(Booking booking, Authentication authentication, RedirectAttributes redirectAttributes) {
        if (booking != null && hotelService.isOwner(booking.getHotel(), authentication)) return false;
        redirectAttributes.addFlashAttribute("errorMessage",
                booking == null ? "Booking not found." : "That booking belongs to a hotel you do not manage.");
        return true;
    }

    @GetMapping
    public String listBookings(Authentication authentication, Model model) {
        if (authentication == null) return "redirect:/login";

        if (isManager(authentication)) {
            // Managers see the reservations of their own properties only
            model.addAttribute("bookings", bookingService.getBookingsForManager(authentication.getName()));
        } else if (isManagerOrAdmin(authentication)) {
            model.addAttribute("bookings", bookingService.getAllBookings());
        } else {
            model.addAttribute("bookings", bookingService.getBookingsByUsername(authentication.getName()));
        }
        // Which rows get "Edit / Cancel" and "Review stay" buttons for the logged-in guest
        @SuppressWarnings("unchecked")
        List<Booking> shown = (List<Booking>) model.getAttribute("bookings");
        String me = authentication.getName();
        Set<Long> editable = new java.util.HashSet<>(), reviewable = new java.util.HashSet<>(), reviewed = new java.util.HashSet<>();
        Set<Long> payable = new java.util.HashSet<>();
        java.util.Map<Long, LocalDate> reviewBy = new java.util.HashMap<>();
        for (Booking b : shown) {
            if (!me.equals(b.getUsername())) continue;
            if (bookingService.isEditable(b)) editable.add(b.getId());
            if (paymentService.isPayable(b, me)) payable.add(b.getId());
            if (reviewService.canReview(b, me)) {
                reviewable.add(b.getId());
                reviewBy.put(b.getId(), ReviewService.reviewDeadline(b));
            }
            else if (reviewService.isStayCompleted(b) && reviewService.hasReview(b)) reviewed.add(b.getId());
        }
        model.addAttribute("editableIds", editable);
        model.addAttribute("reviewableIds", reviewable);
        model.addAttribute("reviewedIds", reviewed);
        model.addAttribute("reviewBy", reviewBy);
        model.addAttribute("payableIds", payable);

        // Proposal: "Tourists can view suggested activities and local experiences alongside their hotel bookings"
        java.util.Map<Booking, List<com.Reservation.Hotel.model.Attraction>> activities = new java.util.LinkedHashMap<>();
        java.util.Set<Long> seenHotels = new java.util.HashSet<>();
        shown.stream()
                .filter(b -> me.equals(b.getUsername()))
                .filter(b -> ("APPROVED".equals(b.getStatus()) || "PENDING".equals(b.getStatus()))
                        && b.getCheckOutDate() != null && !b.getCheckOutDate().isBefore(LocalDate.now()))
                .sorted(java.util.Comparator.comparing(Booking::getCheckInDate))
                .filter(b -> seenHotels.add(b.getHotel().getId()))          // one block per hotel
                .forEach(b -> {
                    List<com.Reservation.Hotel.model.Attraction> near = attractionService.nearHotel(b.getHotel());
                    if (!near.isEmpty()) activities.put(b, near.stream().limit(4).collect(Collectors.toList()));
                });
        model.addAttribute("stayActivities", activities);
        return "bookings/index";
    }

    /**
     * Rooms a guest may pick: every room the manager has left available. Whether a room is free for the
     * chosen dates is checked when the booking is submitted (date-based availability). When editing,
     * the booking's own room is kept in the list even if it has since been marked unavailable.
     */
    private List<Room> bookableRooms(Long hotelId, Long keepRoomId) {
        return roomService.getRoomsByHotelId(hotelId).stream()
                .filter(r -> r.isAvailable() || (keepRoomId != null && keepRoomId.equals(r.getId())))
                .collect(Collectors.toList());
    }

    /**
     * Opened from the hotel page with the rooms the guest ticked (?roomIds=1&roomIds=2) and, when they
     * checked availability first, the dates (?checkIn=..&checkOut=..). The legacy ?roomId=.. also works.
     */
    @GetMapping("/new/{hotelId}")
    public String showBookingForm(@PathVariable Long hotelId,
                                  @RequestParam(value = "roomId", required = false) Long roomId,
                                  @RequestParam(value = "roomIds", required = false) List<Long> roomIds,
                                  @RequestParam(value = "checkIn", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                                  @RequestParam(value = "checkOut", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        Hotel hotel = hotelService.getHotelById(hotelId);
        if (hotel == null) return "redirect:/hotels";
        if (!hotel.isApproved()) {
            redirectAttributes.addFlashAttribute("errorMessage", "This hotel is not open for reservations.");
            return "redirect:/hotels";
        }

        List<Room> rooms = bookableRooms(hotelId, null);
        Set<Long> wanted = new LinkedHashSet<>();
        if (roomIds != null) wanted.addAll(roomIds);
        if (roomId != null) wanted.add(roomId);
        Set<Long> valid = rooms.stream().map(Room::getId).collect(Collectors.toSet());
        if (!valid.containsAll(wanted)) {
            model.addAttribute("errorMessage", "Some of the rooms you picked are no longer available - please choose again.");
            wanted.retainAll(valid);
        }

        // Sensible defaults so the page never opens with a zero-night stay: tonight -> tomorrow
        if (checkIn == null || checkIn.isBefore(LocalDate.now())) checkIn = LocalDate.now();
        if (checkOut == null || !checkOut.isAfter(checkIn)) checkOut = checkIn.plusDays(1);

        model.addAttribute("hotel", hotel);
        model.addAttribute("rooms", rooms);
        model.addAttribute("selectedRoomIds", wanted);
        model.addAttribute("checkIn", checkIn);
        model.addAttribute("checkOut", checkOut);
        model.addAttribute("today", LocalDate.now());
        if (checkIn != null && checkOut != null && checkOut.isAfter(checkIn)) {
            model.addAttribute("freeRoomIds", roomService.freeRoomIds(hotelId, checkIn, checkOut));
        }
        model.addAttribute("activeDiscounts", promotionService.getActiveDiscountsForHotel(hotelId));
        return "bookings/create";
    }

    /**
     * Live availability for the booking page: which of the hotel's rooms are free for the chosen nights.
     * Called whenever the guest changes the dates, so booked rooms are greyed out before they submit.
     */
    @GetMapping("/new/{hotelId}/availability")
    @ResponseBody
    public Map<String, Object> availability(@PathVariable Long hotelId,
                                            @RequestParam("checkIn") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                                            @RequestParam("checkOut") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
        Map<String, Object> result = new LinkedHashMap<>();
        Hotel hotel = hotelService.getHotelById(hotelId);
        String problem = hotel == null || !hotel.isApproved() ? "This hotel is not open for reservations."
                : checkIn.isBefore(LocalDate.now()) ? "Check-in cannot be in the past."
                : !checkOut.isAfter(checkIn) ? "Check-out must be at least one night after check-in."
                : checkIn.isAfter(LocalDate.now().plusYears(1)) ? "Bookings open up to one year ahead."
                : java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut) > 30 ? "A single booking can be for at most 30 nights."
                : null;
        result.put("valid", problem == null);
        result.put("message", problem);
        result.put("nights", problem == null ? java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut) : 0);
        result.put("freeRoomIds", problem == null ? roomService.freeRoomIds(hotelId, checkIn, checkOut) : List.of());
        return result;
    }

    /**
     * Creates one booking per selected room. Several rooms booked together (families / groups, PBI-06)
     * share a group reference and the party is spread over the rooms by their recommended capacity.
     */
    @PostMapping
    public String saveBooking(@RequestParam("hotelId") Long hotelId,
                              @RequestParam(value = "roomIds", required = false) List<Long> roomIds,
                              @RequestParam(value = "checkInDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkInDate,
                              @RequestParam(value = "checkOutDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOutDate,
                              @RequestParam(value = "adults", defaultValue = "1") int adults,
                              @RequestParam(value = "children", defaultValue = "0") int children,
                              @RequestParam(value = "promoCode", required = false) String promoCode,
                              @RequestParam(value = "discountId", required = false) Long discountId,
                              @RequestParam(value = "receiptFile", required = false) MultipartFile receiptFile,
                              @RequestParam(value = "paymentMethod", defaultValue = "BANK_TRANSFER") String paymentMethod,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) throws IOException {

        boolean payByCard = "CARD".equals(paymentMethod);
        Hotel hotel = hotelService.getHotelById(hotelId);
        if (hotel == null || !hotel.isApproved()) {
            redirectAttributes.addFlashAttribute("errorMessage", "That hotel is not open for reservations.");
            return "redirect:/hotels";
        }
        List<Long> ids = roomIds == null ? List.of() : roomIds.stream().distinct().collect(Collectors.toList());
        // On a validation error, send the guest back with their rooms and dates still filled in
        StringBuilder backUrl = new StringBuilder("redirect:/bookings/new/" + hotelId + "?");
        ids.forEach(id -> backUrl.append("roomIds=").append(id).append('&'));
        if (checkInDate != null) backUrl.append("checkIn=").append(checkInDate).append('&');
        if (checkOutDate != null) backUrl.append("checkOut=").append(checkOutDate);
        String back = backUrl.toString();
        if (ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please choose at least one room.");
            return back;
        }
        if (ids.size() > 10) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can book at most 10 rooms at once.");
            return back;
        }
        if (checkInDate == null || checkOutDate == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please provide both check-in and check-out dates.");
            return back;
        }
        if (!checkOutDate.isAfter(checkInDate)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Check-out date must be after the check-in date.");
            return back;
        }
        if (checkInDate.isBefore(LocalDate.now())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Check-in date cannot be in the past.");
            return back;
        }
        if (checkInDate.isAfter(LocalDate.now().plusYears(1))) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bookings open up to one year ahead.");
            return back;
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(checkInDate, checkOutDate) > 30) {
            redirectAttributes.addFlashAttribute("errorMessage", "A single booking can be for at most 30 nights.");
            return back;
        }
        if (adults < 1 || children < 0 || adults > 40 || children > 40) {
            redirectAttributes.addFlashAttribute("errorMessage", "At least one adult is required (max 40 adults and 40 children).");
            return back;
        }
        if (adults < ids.size()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Each room needs at least one adult - add adults or pick fewer rooms.");
            return back;
        }
        if (promoCode != null && !promoCode.isBlank() && promotionService.findValidCoupon(promoCode, hotelId) == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "The coupon code \"" + promoCode.trim() + "\" is not valid right now.");
            return back;
        }

        String receiptError = receiptProblem(receiptFile);
        if (receiptError != null) {
            redirectAttributes.addFlashAttribute("errorMessage", receiptError);
            return back;
        }

        List<Room> rooms = new ArrayList<>();
        for (Long rid : ids) {
            Room room = roomService.getRoomById(rid);
            if (room == null || !room.isAvailable() || room.getHotel() == null || !hotelId.equals(room.getHotel().getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "One of the selected rooms is not available for booking.");
                return back;
            }
            if (!roomService.isRoomFree(rid, checkInDate, checkOutDate, null)) {
                redirectAttributes.addFlashAttribute("errorMessage", "Room #" + room.getRoomNumber()
                        + " is already booked for some of those nights - please choose other rooms or dates.");
                return back;
            }
            rooms.add(room);
        }

        // Spread the party over the rooms: one adult per room first, then fill rooms up to their capacity
        int[][] split = BookingService.splitGuests(rooms, adults, children);
        String group = rooms.size() > 1 ? bookingService.newGroupReference() : null;
        String receiptPath = storeReceipt(receiptFile);

        List<Booking> created = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            Booking booking = new Booking();
            booking.setHotel(hotel);
            booking.setRoom(rooms.get(i));
            booking.setUsername(authentication.getName());
            booking.setCheckInDate(checkInDate);
            booking.setCheckOutDate(checkOutDate);
            booking.setAdults(split[i][0]);
            booking.setChildren(split[i][1]);
            booking.setGuests(split[i][0] + split[i][1]);
            booking.setPromoCode(promoCode == null || promoCode.isBlank() ? null : promoCode.trim().toUpperCase());
            booking.setDiscountId(discountId);
            // Nothing confirmed paid yet at creation time - manager confirms this on approval.
            booking.setAmountPaid(0.0);
            booking.setStatus("PENDING");
            bookingService.recalculatePricingForEdit(booking, rooms.get(i));
            booking.setStatusNote(null);
            booking.setReceiptImagePath(payByCard ? null : receiptPath);
            booking.setPaymentMethod(payByCard ? "CARD" : "BANK_TRANSFER");
            booking.setReference(bookingService.newReference());
            booking.setGroupReference(group);
            booking.setCreatedAt(java.time.LocalDateTime.now());
            created.add(bookingService.saveBooking(booking));
        }

        // Observer: listeners email the guest, alert the manager and write the audit log
        events.publishEvent(new BookingEvent(created, BookingEvent.Type.CREATED, authentication.getName(), null));

        if (payByCard) {
            // Straight to the secure checkout - paying confirms the booking instantly
            return "redirect:/payments/checkout?" + created.stream().map(b -> "bookingIds=" + b.getId()).collect(Collectors.joining("&"));
        }

        String refs = created.stream().map(Booking::getReference).collect(Collectors.joining(", "));
        redirectAttributes.addFlashAttribute("successMessage", created.size() == 1
                ? "Booking " + refs + " submitted! Awaiting manager approval."
                : created.size() + " rooms booked under group " + group + " (" + refs + "). Awaiting manager approval.");
        return "redirect:/bookings";
    }

    // ---------------- Edit ----------------

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Authentication authentication, Model model,
                               RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.getBookingById(id);
        if (booking == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Booking not found.");
            return "redirect:/bookings";
        }
        if (!canAccess(booking, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You cannot edit someone else's booking.");
            return "redirect:/bookings";
        }
        if (!bookingService.isEditable(booking)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "This booking can no longer be edited (it may already be refunding, cancelled, or the stay has started).");
            return "redirect:/bookings";
        }

        model.addAttribute("booking", booking);
        model.addAttribute("hotel", booking.getHotel());
        Long currentRoomId = booking.getRoom() != null ? booking.getRoom().getId() : null;
        model.addAttribute("rooms", bookableRooms(booking.getHotel().getId(), currentRoomId));
        model.addAttribute("activeDiscounts", promotionService.getActiveDiscountsForHotel(booking.getHotel().getId()));
        return "bookings/edit";
    }

    @PostMapping("/{id}/update")
    public String updateBooking(@PathVariable Long id,
                                @RequestParam("roomId") Long roomId,
                                @RequestParam("checkInDate") LocalDate checkInDate,
                                @RequestParam("checkOutDate") LocalDate checkOutDate,
                                @RequestParam(value = "adults", defaultValue = "1") int adults,
                                @RequestParam(value = "children", defaultValue = "0") int children,
                                @RequestParam(value = "promoCode", required = false) String promoCode,
                                @RequestParam(value = "discountId", required = false) Long discountId,
                                @RequestParam(value = "receiptFile", required = false) MultipartFile receiptFile,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) throws IOException {

        Booking booking = bookingService.getBookingById(id);
        if (booking == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Booking not found.");
            return "redirect:/bookings";
        }
        if (!canAccess(booking, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You cannot edit someone else's booking.");
            return "redirect:/bookings";
        }
        if (!bookingService.isEditable(booking)) {
            redirectAttributes.addFlashAttribute("errorMessage", "This booking can no longer be edited.");
            return "redirect:/bookings";
        }

        String receiptError = receiptProblem(receiptFile);
        if (receiptError != null) {
            redirectAttributes.addFlashAttribute("errorMessage", receiptError);
            return "redirect:/bookings/" + id + "/edit";
        }

        Room room = roomService.getRoomById(roomId);
        if (room == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "That room is no longer available.");
            return "redirect:/bookings/" + id + "/edit";
        }
        boolean sameRoom = booking.getRoom() != null && booking.getRoom().getId().equals(room.getId());
        boolean sameHotel = room.getHotel() != null && booking.getHotel() != null
                && room.getHotel().getId().equals(booking.getHotel().getId());
        if (!sameHotel || (!room.isAvailable() && !sameRoom)) {
            redirectAttributes.addFlashAttribute("errorMessage", "That room is not available for booking.");
            return "redirect:/bookings/" + id + "/edit";
        }
        if (!checkOutDate.isAfter(checkInDate)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Check-out date must be after the check-in date.");
            return "redirect:/bookings/" + id + "/edit";
        }
        if (checkInDate.isBefore(LocalDate.now())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Check-in date cannot be in the past.");
            return "redirect:/bookings/" + id + "/edit";
        }
        if (!roomService.isRoomFree(room.getId(), checkInDate, checkOutDate, booking.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "That room is already booked for some of those nights - please choose another room or other dates.");
            return "redirect:/bookings/" + id + "/edit";
        }
        if (promoCode != null && !promoCode.isBlank()
                && promotionService.findValidCoupon(promoCode, booking.getHotel().getId()) == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "The coupon code \"" + promoCode.trim() + "\" is not valid right now.");
            return "redirect:/bookings/" + id + "/edit";
        }
        if (adults < 1 || children < 0 || adults > 20 || children > 20) {
            redirectAttributes.addFlashAttribute("errorMessage", "At least one adult is required (max 20 adults and 20 children).");
            return "redirect:/bookings/" + id + "/edit";
        }
        if (!checkOutDate.isAfter(checkInDate)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Check-out date must be after the check-in date.");
            return "redirect:/bookings/" + id + "/edit";
        }

        booking.setRoom(room);
        booking.setCheckInDate(checkInDate);
        booking.setCheckOutDate(checkOutDate);
        booking.setAdults(adults);
        booking.setChildren(children);
        booking.setGuests(adults + children);
        booking.setPromoCode(promoCode == null || promoCode.isBlank() ? null : promoCode.trim().toUpperCase());
        booking.setDiscountId(discountId);

        bookingService.recalculatePricingForEdit(booking, room);
        saveReceiptIfPresent(booking, receiptFile);
        if (bookingService.saveEdit(booking) != null) {
            // Another guest secured the room for those nights a moment ago - nothing was changed
            redirectAttributes.addFlashAttribute("errorMessage",
                    "That room was just booked by another guest for some of those nights - please choose another room or other dates.");
            return "redirect:/bookings/" + id + "/edit";
        }

        String msg;
        if ("REFUND_PENDING".equals(booking.getStatus())) {
            msg = String.format("Booking updated. A refund of $%.2f is due - the manager will process it shortly.",
                    -booking.getBalanceDue());
        } else if (booking.getBalanceDue() > 0.01) {
            msg = String.format("Booking updated. An additional $%.2f is due - please wait for manager approval.",
                    booking.getBalanceDue());
        } else {
            msg = "Booking updated successfully.";
        }
        redirectAttributes.addFlashAttribute("successMessage", msg);
        return "redirect:/bookings";
    }

    // ---------------- Cancel (soft delete) ----------------

    @PostMapping("/{id}/cancel")
    public String cancelBooking(@PathVariable Long id, Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.getBookingById(id);
        if (booking == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Booking not found.");
            return "redirect:/bookings";
        }
        if (!canAccess(booking, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You cannot cancel someone else's booking.");
            return "redirect:/bookings";
        }
        if (!bookingService.isEditable(booking)) {
            redirectAttributes.addFlashAttribute("errorMessage", "This booking can no longer be cancelled.");
            return "redirect:/bookings";
        }

        bookingService.cancelBooking(id);
        events.publishEvent(new BookingEvent(booking, BookingEvent.Type.CANCELLED_BY_GUEST, authentication.getName(), null));
        redirectAttributes.addFlashAttribute("successMessage", "Booking cancelled.");
        return "redirect:/bookings";
    }

    // ---------------- Manager cancels a booking (e.g. guest left before check-out) ----------------

    @PostMapping("/{id}/manager-cancel")
    public String managerCancelBooking(@PathVariable Long id,
                                       @RequestParam(value = "reason", required = false) String reason,
                                       @RequestParam(value = "refund", required = false, defaultValue = "false") boolean refund,
                                       Authentication authentication,
                                       RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.getBookingById(id);
        if (notOwner(booking, authentication, redirectAttributes)) return "redirect:/bookings";
        String problem = bookingService.managerCancelBooking(booking, reason, refund);
        if (problem == null) {
            events.publishEvent(new BookingEvent(booking, BookingEvent.Type.CANCELLED_BY_HOTEL, authentication.getName(), reason));
        }
        if (problem != null) {
            redirectAttributes.addFlashAttribute("errorMessage", problem);
        } else if ("REFUND_PENDING".equals(booking.getStatus())) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Booking cancelled and the room is available again. A refund is now pending.");
        } else {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Booking cancelled and the room is available again.");
        }
        return "redirect:/bookings";
    }

    // ---------------- Manager-only hard delete (closed bookings only) ----------------

    @PostMapping("/{id}/delete")
    public String deleteBooking(@PathVariable Long id, Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        if (!isManager(authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only a manager can delete booking records.");
            return "redirect:/bookings";
        }
        Booking booking = bookingService.getBookingById(id);
        if (notOwner(booking, authentication, redirectAttributes)) return "redirect:/bookings";
        if (booking == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Booking not found.");
            return "redirect:/bookings";
        }
        if (!bookingService.isClosed(booking)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Only cancelled or fully-refunded bookings can be deleted.");
            return "redirect:/bookings";
        }

        bookingService.deleteBookingById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Booking record deleted.");
        return "redirect:/bookings";
    }

    // ---------------- Manager actions ----------------

    @PostMapping("/{id}/approve")
    public String approveBooking(@PathVariable Long id, Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.getBookingById(id);
        if (notOwner(booking, authentication, redirectAttributes)) return "redirect:/bookings";
        if (booking == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Booking not found.");
            return "redirect:/bookings";
        }
        if (!"PENDING".equals(booking.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only pending bookings can be approved.");
            return "redirect:/bookings";
        }

        // Approving reserves the room and confirms the money as received - unless another approved booking
        // already holds it (checked under a room lock, so two simultaneous approvals cannot both succeed).
        Booking conflict = bookingService.approve(booking);
        if (conflict != null) {
            redirectAttributes.addFlashAttribute("errorMessage", String.format(
                    "Cannot approve: this room is already booked by %s from %s to %s. " +
                            "Reject this request or release the room first.",
                    conflict.getDisplayReference(), conflict.getCheckInDate(), conflict.getCheckOutDate()));
            return "redirect:/bookings";
        }

        events.publishEvent(new BookingEvent(booking, BookingEvent.Type.APPROVED, authentication.getName(), null));
        redirectAttributes.addFlashAttribute("successMessage", "Booking " + booking.getDisplayReference()
                + " approved - the guest has been sent the confirmation and receipt.");
        return "redirect:/bookings";
    }

    /** Manager can reject a pending request, or an already-approved booking (the room is freed again). */
    @PostMapping("/{id}/reject")
    public String rejectBooking(@PathVariable Long id, @RequestParam("reason") String reason,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.getBookingById(id);
        if (notOwner(booking, authentication, redirectAttributes)) return "redirect:/bookings";
        if (booking == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Booking not found.");
            return "redirect:/bookings";
        }
        if (!"PENDING".equals(booking.getStatus()) && !"APPROVED".equals(booking.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Only pending or approved bookings can be rejected.");
            return "redirect:/bookings";
        }
        boolean wasApproved = "APPROVED".equals(booking.getStatus());

        booking.setStatus("REFUND_PENDING");
        booking.setStatusNote(reason);
        booking.setBalanceDue(-booking.getAmountPaid());
        booking.setRefundId("REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        bookingService.saveBooking(booking);
        events.publishEvent(new BookingEvent(booking, BookingEvent.Type.REJECTED, authentication.getName(), reason));
        redirectAttributes.addFlashAttribute("successMessage", wasApproved
                ? "Booking rejected. Refund set to PENDING and the room is available again."
                : "Booking rejected. Refund set to PENDING.");
        return "redirect:/bookings";
    }

    /**
     * Manager marks the room as available again before the check-out date (e.g. the guest left early).
     * Only the room hold is lifted - the booking's other details are not touched.
     * {@code redirect=rooms} sends the manager back to the hotel's rooms page instead of the bookings list.
     */
    @PostMapping("/{id}/release-room")
    public String releaseRoom(@PathVariable Long id,
                              @RequestParam(value = "redirect", required = false) String redirect,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.getBookingById(id);
        if (notOwner(booking, authentication, redirectAttributes)) return "redirect:/bookings";
        String problem = bookingService.releaseRoom(booking);

        if (problem != null) {
            redirectAttributes.addFlashAttribute("errorMessage", problem);
        } else {
            String roomNo = booking.getRoom() != null ? booking.getRoom().getRoomNumber() : "";
            redirectAttributes.addFlashAttribute("successMessage",
                    "Room #" + roomNo + " is available again for new guests.");
        }

        if ("rooms".equals(redirect) && booking != null && booking.getHotel() != null) {
            return "redirect:/hotels/" + booking.getHotel().getId() + "/rooms";
        }
        return "redirect:/bookings";
    }

    @PostMapping("/{id}/settle-refund")
    public String settleRefund(@PathVariable Long id, Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.getBookingById(id);
        if (notOwner(booking, authentication, redirectAttributes)) return "redirect:/bookings";
        if ("REFUND_PENDING".equals(booking.getStatus())) {
            booking.setStatus("REFUND_COMPLETED");
            // The refund has now been paid out externally, so the books are settled.
            booking.setAmountPaid(booking.getFinalAmount());
            booking.setBalanceDue(0.0);
            bookingService.saveBooking(booking);
            events.publishEvent(new BookingEvent(booking, BookingEvent.Type.REFUND_COMPLETED, authentication.getName(), null));
            redirectAttributes.addFlashAttribute("successMessage", "External refund completed!");
        }
        return "redirect:/bookings";
    }

    // ---------------- User acknowledgment ----------------

    @PostMapping("/{id}/accept-refund")
    public String acceptRefund(@PathVariable Long id, Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        Booking booking = bookingService.getBookingById(id);
        if (booking != null && canAccess(booking, authentication) && "REFUND_COMPLETED".equals(booking.getStatus())) {
            booking.setStatus("REFUND_ACCEPTED");
            bookingService.saveBooking(booking);
            redirectAttributes.addFlashAttribute("successMessage", "Refund process closed.");
        }
        return "redirect:/bookings";
    }

    // ---------------- Shared helper ----------------

    /** Stores an uploaded payment receipt and returns its public path, or null when no file was sent. */
    private String storeReceipt(MultipartFile receiptFile) throws IOException {
        if (receiptFile == null || receiptFile.isEmpty()) return null;
        Booking holder = new Booking();
        saveReceiptIfPresent(holder, receiptFile);
        return holder.getReceiptImagePath();
    }

    private static final Set<String> RECEIPT_TYPES = Set.of("jpg", "jpeg", "png", "webp", "pdf");

    private static String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    /** Receipts are served from this site, so only images and PDFs are accepted (never HTML/scripts). */
    private String receiptProblem(MultipartFile receiptFile) {
        if (receiptFile == null || receiptFile.isEmpty()) return null;
        if (!RECEIPT_TYPES.contains(extensionOf(receiptFile.getOriginalFilename()))) {
            return "Payment receipts must be a JPG, PNG, WEBP or PDF file.";
        }
        return null;
    }

    /**
     * Serves an uploaded payment receipt. Receipts are personal financial documents, so only the guest who made
     * the booking, the manager of that hotel and administrators may open them (/uploads/receipts/** is blocked).
     */
    @GetMapping("/{id}/receipt-file")
    public ResponseEntity<Resource> receiptFile(@PathVariable Long id, Authentication authentication) throws IOException {
        Booking booking = bookingService.getBookingById(id);
        if (booking == null || booking.getReceiptImagePath() == null) return ResponseEntity.notFound().build();
        if (!canAccess(booking, authentication) && !hotelService.canManage(booking.getHotel(), authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Path folder = RECEIPT_FOLDER.toAbsolutePath().normalize();
        Path file = folder.resolve(Paths.get(booking.getReceiptImagePath()).getFileName().toString()).normalize();
        if (!file.startsWith(folder) || !Files.isRegularFile(file)) return ResponseEntity.notFound().build();

        String type = switch (extensionOf(file.getFileName().toString())) {
            case "pdf" -> MediaType.APPLICATION_PDF_VALUE;
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "webp" -> "image/webp";
            default -> MediaType.IMAGE_JPEG_VALUE;
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"receipt-" + booking.getId() + "."
                        + extensionOf(file.getFileName().toString()) + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new FileSystemResource(file));
    }

    private static final Path RECEIPT_FOLDER = Paths.get("uploads", "receipts");

    private void saveReceiptIfPresent(Booking booking, MultipartFile receiptFile) throws IOException {
        if (receiptFile != null && !receiptFile.isEmpty() && receiptProblem(receiptFile) == null) {
            String fileName = UUID.randomUUID() + "." + extensionOf(receiptFile.getOriginalFilename());
            Path uploadPath = RECEIPT_FOLDER;
            if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);
            Files.copy(receiptFile.getInputStream(), uploadPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            booking.setReceiptImagePath("/uploads/receipts/" + fileName);
        }
    }
}