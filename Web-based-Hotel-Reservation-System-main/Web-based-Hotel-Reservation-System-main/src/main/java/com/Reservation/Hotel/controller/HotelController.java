package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.dto.HotelSearchCriteria;
import com.Reservation.Hotel.dto.RatingStats;
import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.service.HotelImageStorageService;
import com.Reservation.Hotel.service.HotelSearchService;
import com.Reservation.Hotel.service.HotelService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import com.Reservation.Hotel.events.HotelEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/hotels")
public class HotelController {

    /** Amenities the manager can tick (label -> Bootstrap icon). Add new ones here. */
    private static final int REVIEWS_PER_PAGE = 5;
    private static final Map<String, String> BASE_AMENITIES = new LinkedHashMap<>();
    static {
        BASE_AMENITIES.put("Free WiFi", "bi-wifi");
        BASE_AMENITIES.put("Swimming Pool", "bi-water");
        BASE_AMENITIES.put("Spa & Wellness", "bi-flower1");
        BASE_AMENITIES.put("Fitness Center", "bi-heart-pulse");
        BASE_AMENITIES.put("Restaurant", "bi-cup-hot");
        BASE_AMENITIES.put("Rooftop Bar", "bi-cup-straw");
        BASE_AMENITIES.put("Airport Shuttle", "bi-bus-front");
        BASE_AMENITIES.put("Free Parking", "bi-car-front");
        BASE_AMENITIES.put("Air Conditioning", "bi-snow");
        BASE_AMENITIES.put("Room Service", "bi-bell");
        BASE_AMENITIES.put("24/7 Front Desk", "bi-clock");
        BASE_AMENITIES.put("Beach Access", "bi-umbrella");
        BASE_AMENITIES.put("Conference Hall", "bi-briefcase");
        BASE_AMENITIES.put("Kids Club", "bi-balloon");
        BASE_AMENITIES.put("Pet Friendly", "bi-emoji-smile");
        BASE_AMENITIES.put("Laundry Service", "bi-basket");
        BASE_AMENITIES.put("Concierge", "bi-person-badge");
        BASE_AMENITIES.put("Garden", "bi-tree");
    }

    private final HotelService hotelService;
    private final HotelImageStorageService imageStorage;
    private final RoomService roomService;
    private final UserService userService;
    private final HotelSearchService hotelSearchService;
    private final ReviewService reviewService;
    private final RatingProvider ratingProvider;
    private final AttractionService attractionService;

    private final ApplicationEventPublisher events;

    public HotelController(HotelService hotelService, HotelImageStorageService imageStorage,
                           RoomService roomService, UserService userService,
                           HotelSearchService hotelSearchService, ReviewService reviewService,
                           RatingProvider ratingProvider, AttractionService attractionService, ApplicationEventPublisher events) {
        this.events = events;
        this.attractionService = attractionService;
        this.reviewService = reviewService;
        this.ratingProvider = ratingProvider;
        this.hotelSearchService = hotelSearchService;
        this.hotelService = hotelService;
        this.imageStorage = imageStorage;
        this.roomService = roomService;
        this.userService = userService;
    }

    // ---------------- helpers ----------------

    private List<String> splitAmenities(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** Predefined options, plus any older free-typed amenity the hotel already has (so it isn't lost on edit). */
    private Map<String, String> buildAmenityOptions(Collection<String> extra) {
        Map<String, String> options = new LinkedHashMap<>(BASE_AMENITIES);
        for (String a : extra) {
            options.putIfAbsent(a, "bi-check2-circle");
        }
        return options;
    }

    /** Keeps only values that are really in the allowed list (nobody can inject arbitrary text). */
    private List<String> sanitizeAmenities(List<String> submitted, Collection<String> allowed) {
        if (submitted == null) return new ArrayList<>();
        return submitted.stream()
                .map(String::trim)
                .filter(allowed::contains)
                .distinct()
                .collect(Collectors.toList());
    }

    private void prepareForm(Model model, List<String> selected, Collection<String> legacyAmenities) {
        model.addAttribute("managers", userService.findByRole(AppUser.ROLE_MANAGER));
        model.addAttribute("amenityOptions", buildAmenityOptions(legacyAmenities));
        model.addAttribute("selectedAmenities", selected);
        model.addAttribute("maxImages", HotelImageStorageService.MAX_IMAGES);
    }

    /** Amenity names offered in the search filters. */
    public static Collection<String> amenityNames() {
        return BASE_AMENITIES.keySet();
    }

    public static Map<String, String> amenityIcons() {
        return BASE_AMENITIES;
    }

    // ---------------- list ----------------

    @GetMapping
    public String listHotels(@RequestParam(value = "keyword", required = false) String keyword,
                             @RequestParam(value = "status", required = false) String status,
                             @ModelAttribute("criteria") HotelSearchCriteria criteria,
                             Authentication authentication,
                             Model model) {

        List<Hotel> hotels;
        if (HotelService.hasRole(authentication, "ADMIN")) {
            if (status == null) status = "ALL";
            hotels = hotelService.filterAndSearchHotels(status, keyword);
        } else if (HotelService.hasRole(authentication, "MANAGER")) {
            // A manager works with their own properties only, whatever their status
            if (status == null) status = "ALL";
            hotels = hotelService.filterManagerHotels(authentication.getName(), status, keyword);
        } else {
            // Visitors, tourists and agents search APPROVED hotels only
            if (criteria.getCheckIn() != null && criteria.getCheckOut() != null && !criteria.hasDates()) {
                model.addAttribute("errorMessage", "Check-out must be after check-in.");
                criteria.setCheckOut(null);
            }
            if (criteria.getCheckIn() != null && criteria.getCheckIn().isBefore(LocalDate.now())) {
                model.addAttribute("errorMessage", "Check-in cannot be in the past.");
                criteria.setCheckIn(null);
                criteria.setCheckOut(null);
            }
            model.addAttribute("results", hotelSearchService.search(criteria));
            model.addAttribute("amenityIcons", BASE_AMENITIES);
            model.addAttribute("today", LocalDate.now());
            return "hotels/search";
        }

        model.addAttribute("hotels", hotels);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentStatus", status);
        return "hotels/index";
    }

    // ---------------- public detail page ----------------

    @GetMapping("/{id:\\d+}")
    public String showHotel(@PathVariable Long id,
                            @RequestParam(value = "checkIn", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                            @RequestParam(value = "checkOut", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                            @RequestParam(value = "guests", required = false) Integer guests,
                            @RequestParam(value = "reviewPage", defaultValue = "1") int reviewPage,
                            Authentication authentication, Model model,
                            RedirectAttributes redirectAttributes) {
        Hotel hotel = hotelService.getHotelById(id);
        if (hotel == null || (!hotel.isApproved() && !hotelService.canManage(hotel, authentication))) {
            redirectAttributes.addFlashAttribute("errorMessage", "That hotel is not available.");
            return "redirect:/hotels";
        }
        List<Room> rooms = roomService.getRoomsWithReservationStatus(id);
        model.addAttribute("hotel", hotel);
        model.addAttribute("rooms", rooms.stream().filter(Room::isAvailable).collect(Collectors.toList()));
        model.addAttribute("amenityIcons", BASE_AMENITIES);
        model.addAttribute("canManage", hotelService.canManage(hotel, authentication));
        model.addAttribute("managerName", userService.displayNameOf(hotel.getManagerUsername()));
        model.addAttribute("today", LocalDate.now());

        // Reviews: admins also see hidden ones (to restore them); averages count visible reviews only.
        // The list is paginated (T-14.2) so popular hotels don't render hundreds of reviews at once.
        List<Review> visible = reviewService.visibleForHotel(id);
        List<Review> listed = HotelService.hasRole(authentication, "ADMIN") ? reviewService.allForHotel(id) : visible;
        int reviewPages = Math.max(1, (listed.size() + REVIEWS_PER_PAGE - 1) / REVIEWS_PER_PAGE);
        int page = Math.min(Math.max(reviewPage, 1), reviewPages);
        int from = (page - 1) * REVIEWS_PER_PAGE;
        model.addAttribute("reviews", listed.subList(from, Math.min(from + REVIEWS_PER_PAGE, listed.size())));
        model.addAttribute("reviewPage", page);
        model.addAttribute("reviewPages", reviewPages);
        model.addAttribute("distribution", reviewService.distribution(visible));
        model.addAttribute("stats", new RatingStats(ratingProvider.averageRating(id), visible.size()));
        model.addAttribute("isOwner", hotelService.isOwner(hotel, authentication));
        model.addAttribute("attractions", attractionService.nearHotel(hotel));
        model.addAttribute("attractionCategories", Attraction.CATEGORIES);

        // Real-time availability for the requested stay
        if (checkIn != null && checkOut != null) {
            if (!checkOut.isAfter(checkIn)) {
                model.addAttribute("dateError", "Check-out must be after check-in.");
            } else if (checkIn.isBefore(LocalDate.now())) {
                model.addAttribute("dateError", "Check-in cannot be in the past.");
            } else {
                model.addAttribute("checkIn", checkIn);
                model.addAttribute("checkOut", checkOut);
                model.addAttribute("guests", guests);
                Set<Long> free = roomService.freeRoomIds(id, checkIn, checkOut);
                model.addAttribute("freeRoomIds", free);
                int sleeps = rooms.stream().filter(r -> free.contains(r.getId()))
                        .mapToInt(r -> r.getMaxGuests() == null ? 0 : r.getMaxGuests()).sum();
                if (free.isEmpty()) {
                    model.addAttribute("dateError", "No rooms are free for those dates - try other dates.");
                } else if (guests != null && sleeps < guests) {
                    model.addAttribute("dateError", "Only " + sleeps + " guests can be accommodated for those dates.");
                }
            }
        }
        return "hotels/show";
    }

    // ---------------- create ----------------

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("hotel", new Hotel());
        prepareForm(model, new ArrayList<>(), List.of());
        return "hotels/create";
    }

    @PostMapping
    public String saveHotel(@Valid @ModelAttribute("hotel") Hotel hotel,
                            BindingResult bindingResult,
                            @RequestParam(value = "amenityList", required = false) List<String> amenityList,
                            @RequestParam(value = "images", required = false) MultipartFile[] images,
                            @RequestParam(value = "ownerUsername", required = false) String ownerUsername,
                            Authentication authentication,
                            Model model,
                            RedirectAttributes redirectAttributes) throws IOException {

        List<String> selected = sanitizeAmenities(amenityList, BASE_AMENITIES.keySet());
        List<MultipartFile> files = imageStorage.nonEmpty(images);

        String imageError = imageStorage.validate(files, 0);
        if (imageError == null && files.isEmpty()) {
            imageError = "Please upload at least one photo of the hotel.";
        }

        // Managers own what they register; an admin registers on behalf of a chosen manager
        boolean admin = HotelService.hasRole(authentication, "ADMIN");
        String owner = authentication.getName();
        if (admin) {
            owner = userService.findByUsername(ownerUsername)
                    .filter(u -> AppUser.ROLE_MANAGER.equals(u.getRole()))
                    .map(AppUser::getUsername).orElse(null);
            if (owner == null) model.addAttribute("ownerError", "Choose the hotel manager who will run this property.");
        }

        if (bindingResult.hasErrors() || imageError != null || model.containsAttribute("ownerError")) {
            model.addAttribute("imageError", imageError);
            prepareForm(model, selected, List.of());
            return "hotels/create";
        }

        // Never trust these from the form
        hotel.setId(null);
        hotel.setManagerUsername(owner);
        // Hotels registered by an admin are already verified
        hotel.setStatus(admin ? Hotel.APPROVED : Hotel.PENDING);
        hotel.setStatusNote(null);
        hotel.setAmenities(String.join(",", selected));
        hotel.setImagePaths(new ArrayList<>(imageStorage.store(files)));

        hotelService.saveHotel(hotel);
        events.publishEvent(new HotelEvent(hotel, HotelEvent.Type.REGISTERED, authentication.getName(), "owner " + owner));
        redirectAttributes.addFlashAttribute("successMessage", admin
                ? "Hotel registered and approved."
                : "Hotel registered! It will be visible to tourists once an administrator approves it.");
        return "redirect:/hotels";
    }

    // ---------------- approve / reject / remove ----------------

    @PostMapping("/{id}/approve")
    public String approveHotel(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        Hotel hotel = hotelService.getHotelById(id);
        if (hotel != null && !Hotel.REMOVED.equals(hotel.getStatus())) {
            hotelService.approve(hotel, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Hotel approved and the manager has been notified.");
        }
        return "redirect:/hotels?status=PENDING";
    }

    @PostMapping("/{id}/reject")
    public String rejectHotel(@PathVariable Long id, @RequestParam(value = "reason", required = false) String reason,
                              Authentication authentication, RedirectAttributes redirectAttributes) {
        Hotel hotel = hotelService.getHotelById(id);
        if (hotel != null && Hotel.PENDING.equals(hotel.getStatus())) {
            hotelService.reject(hotel, reason, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Hotel registration rejected and the manager has been notified.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Only pending hotels can be rejected.");
        }
        return "redirect:/hotels?status=PENDING";
    }

    @PostMapping("/{id}/delete")
    public String deleteHotel(@PathVariable Long id,
                              @RequestParam(value = "reason", required = false) String reason,
                              Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        Hotel hotel = hotelService.getHotelById(id);
        if (!hotelService.canManage(hotel, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can only remove hotels you manage.");
            return "redirect:/hotels";
        }
        String summary = hotelService.removeHotel(id, reason, authentication.getName());
        redirectAttributes.addFlashAttribute("successMessage", summary);
        return "redirect:/hotels";
    }

    // ---------------- edit ----------------

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Authentication authentication, Model model,
                               RedirectAttributes redirectAttributes) {
        Hotel hotel = hotelService.getHotelById(id);
        if (!hotelService.canManage(hotel, authentication) || Hotel.REMOVED.equals(hotel.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can only edit hotels you manage.");
            return "redirect:/hotels";
        }
        List<String> current = splitAmenities(hotel.getAmenities());
        roomService.markReservations(hotel.getRooms());   // so the rooms table can show "Reserved"
        model.addAttribute("hotel", hotel);
        prepareForm(model, current, current);
        return "hotels/edit";
    }

    @PostMapping("/{id}/update")
    public String updateHotel(@PathVariable Long id,
                              @Valid @ModelAttribute("hotel") Hotel hotel,
                              BindingResult bindingResult,
                              @RequestParam(value = "amenityList", required = false) List<String> amenityList,
                              @RequestParam(value = "removeImages", required = false) List<String> removeImages,
                              @RequestParam(value = "images", required = false) MultipartFile[] images,
                              Authentication authentication,
                              Model model,
                              RedirectAttributes redirectAttributes) throws IOException {

        Hotel existing = hotelService.getHotelById(id);
        if (!hotelService.canManage(existing, authentication) || Hotel.REMOVED.equals(existing.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can only edit hotels you manage.");
            return "redirect:/hotels";
        }

        List<String> legacy = splitAmenities(existing.getAmenities());
        List<String> allowed = new ArrayList<>(BASE_AMENITIES.keySet());
        allowed.addAll(legacy);
        List<String> selected = sanitizeAmenities(amenityList, allowed);

        // Only photos that really belong to this hotel can be removed
        List<String> currentImages = existing.getImagePaths();
        List<String> toRemove = removeImages == null ? new ArrayList<>()
                : removeImages.stream().filter(currentImages::contains).distinct().collect(Collectors.toList());
        int kept = currentImages.size() - toRemove.size();

        List<MultipartFile> files = imageStorage.nonEmpty(images);
        String imageError = imageStorage.validate(files, kept);
        if (imageError == null && kept + files.size() == 0) {
            imageError = "A hotel must keep at least one photo.";
        }

        if (bindingResult.hasErrors() || imageError != null) {
            // Re-fill the fields the form doesn't post, so the page renders correctly again
            hotel.setId(id);
            hotel.setStatus(existing.getStatus());
            hotel.setStatusNote(existing.getStatusNote());
            hotel.setRooms(existing.getRooms());
            roomService.markReservations(existing.getRooms());
            hotel.setImagePaths(currentImages);
            model.addAttribute("imageError", imageError);
            prepareForm(model, selected, legacy);
            return "hotels/edit";
        }

        hotel.setAmenities(String.join(",", selected));
        List<String> newPaths = imageStorage.store(files);
        hotelService.updateHotel(id, hotel, toRemove, newPaths);

        String msg = "Hotel updated successfully!";
        if (Hotel.REJECTED.equals(existing.getStatus()) && hotelService.isOwner(existing, authentication)) {
            hotelService.resubmit(existing);
            msg = "Hotel updated and re-submitted for administrator approval.";
        }
        redirectAttributes.addFlashAttribute("successMessage", msg);
        return "redirect:/hotels";
    }
}
