package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.dto.HotelSearchCriteria;
import com.Reservation.Hotel.dto.HotelSearchResult;
import com.Reservation.Hotel.model.AppUser;
import com.Reservation.Hotel.model.Attraction;
import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.model.Promotion;
import com.Reservation.Hotel.service.AgentService;
import com.Reservation.Hotel.service.AttractionService;
import com.Reservation.Hotel.service.HotelService;
import com.Reservation.Hotel.service.HotelSearchService;
import com.Reservation.Hotel.service.PromotionService;
import com.Reservation.Hotel.service.ReviewService;
import com.Reservation.Hotel.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    /** A destination tile / map pin on the home page. */
    public static class Destination {
        private final String name, region, tagline, image, labelSide;
        private final long stays;
        private final double mapX, mapY;
        private final boolean featured;

        public Destination(String name, String region, String tagline, String image, long stays,
                           double mapX, double mapY, String labelSide, boolean featured) {
            this.name = name;
            this.region = region;
            this.tagline = tagline;
            this.image = image;
            this.stays = stays;
            this.mapX = mapX;
            this.mapY = mapY;
            this.labelSide = labelSide;
            this.featured = featured;
        }

        public String getName() { return name; }
        public String getRegion() { return region; }
        public String getTagline() { return tagline; }
        public String getImage() { return image; }
        public long getStays() { return stays; }
        /** Position of the town on the home page island map (SVG units, viewBox 0 0 430 725). */
        public double getMapX() { return mapX; }
        public double getMapY() { return mapY; }
        /** Where the pin's name goes: l(eft), r(ight) or b(elow). */
        public String getLabelSide() { return labelSide; }
        /** The six headline destinations get the big pins and the photo tiles. */
        public boolean isFeatured() { return featured; }
    }

    /**
     * Destinations (name used as the search keyword): region, tagline, photo, and position on the island map
     * (x = (longitude - 79.6) * 160 + 20, y = (9.95 - latitude) * 160 + 14) plus the side for the pin label.
     * The first six are the featured ones shown as photo tiles.
     */
    private static final List<String[]> DESTINATIONS = List.of(
            new String[]{"Galle", "Southern coast", "Dutch fort, sunsets & boutique villas", "/images/stays/sunset-pool.jpg", "119.2", "641.2", "l"},
            new String[]{"Mirissa", "Southern coast", "Whale watching & palm-lined bays", "/images/stays/beach-deck.jpg", "157.6", "654", "b"},
            new String[]{"Ella", "Hill country", "Tea hills, trails & the Nine Arch Bridge", "/images/stays/nine-arch-bridge.jpg", "252", "506.8", "r"},
            new String[]{"Sigiriya", "Cultural triangle", "The Lion Rock & jungle hideaways", "/images/stays/rock-view-pool.jpg", "205.6", "332.4", "b"},
            new String[]{"Nuwara Eliya", "Hill country", "Colonial charm in 'Little England'", "/images/stays/hill-country-hotel.jpg", "208.8", "490.8", "l"},
            new String[]{"Arugam Bay", "East coast", "World-class surf & laid-back beaches", "/images/stays/surf-beach.jpg", "378.4", "511.6", "b"},
            new String[]{"Colombo", "Western coast", "Capital buzz, Galle Face Green & rooftop bars", "/images/stays/night-pool-villa.jpg", "61.6", "497.2", "b"},
            new String[]{"Negombo", "Western coast", "Lagoon fishing town minutes from the airport", "/images/stays/beach-resort-lawn.jpg", "58.4", "452.4", "r"},
            new String[]{"Kurunegala", "North Western", "Elephant Rock & quiet temple towns", "/images/stays/white-villa.jpg", "141.6", "407.6", "l"},
            new String[]{"Dambulla", "Cultural triangle", "Golden cave temples over 2,000 years old", "/images/stays/hillside-terrace.jpg", "188", "348.4", "l"},
            new String[]{"Anuradhapura", "Cultural triangle", "Sacred ancient capital & giant stupas", "/images/stays/resort-garden-pool.jpg", "148", "276.4", "r"},
            new String[]{"Mannar", "Northern Province", "Baobab trees, flamingos & quiet beaches", "/images/stays/beach-deck.jpg", "68", "169.2", "r"},
            new String[]{"Jaffna", "Northern Province", "Tamil culture, Hindu temples & lagoon islands", "/images/stays/white-villa.jpg", "85.6", "60.4", "r"},
            new String[]{"Vavuniya", "Northern Province", "The gateway to the north", "/images/stays/resort-garden-pool.jpg", "164", "206", "r"},
            new String[]{"Trincomalee", "East coast", "Nilaveli beaches & a deep natural harbour", "/images/stays/ocean-infinity-pool.jpg", "277.6", "234.8", "r"},
            new String[]{"Batticaloa", "East coast", "Lagoons, a lighthouse & the singing fish", "/images/stays/sunset-pool.jpg", "354.4", "370.8", "l"},
            new String[]{"Polonnaruwa", "Cultural triangle", "Medieval royal city & the Gal Vihara", "/images/stays/lakeside-dining.jpg", "244", "335.6", "r"},
            new String[]{"Kandy", "Hill country", "Temple of the Tooth & lakeside charm", "/images/stays/hill-country-hotel.jpg", "184.8", "439.6", "r"},
            new String[]{"Yala", "Deep south", "Leopards, elephants & a wild coastline", "/images/stays/resort-garden-pool.jpg", "316", "582", "r"},
            new String[]{"Hambantota", "Deep south", "Bird sanctuaries & quiet golden sand", "/images/stays/sunset-pool.jpg", "263.2", "626.8", "l"},
            new String[]{"Bentota", "South-west coast", "River safaris, turtles & Ayurveda", "/images/stays/deck-infinity-pool.jpg", "84", "578.8", "r"},
            new String[]{"Ratnapura", "Sabaragamuwa", "City of gems & rainforest trails", "/images/stays/hillside-terrace.jpg", "148", "537.2", "l"}
    );

    /** The grand island loop drawn on the map, in travel order (starts and ends in Colombo). */
    private static final List<String> ROUTE = List.of("Colombo", "Negombo", "Kurunegala", "Dambulla", "Anuradhapura", "Mannar",
            "Jaffna", "Vavuniya", "Trincomalee", "Batticaloa", "Polonnaruwa", "Sigiriya", "Kandy", "Nuwara Eliya", "Ella",
            "Arugam Bay", "Yala", "Hambantota", "Mirissa", "Galle", "Bentota", "Ratnapura");

    /**
     * Photos that really show a particular attraction. Attractions without one do not get a stand-in photo
     * (a wrong picture is worse than none).
     */
    public static final Map<String, String> ATTRACTION_IMAGES = Map.of(
            "Nine Arch Bridge", "/images/stays/nine-arch-bridge.jpg",
            "Sigiriya Rock Fortress", "/images/stays/rock-view-pool.jpg",
            "Galle Face Green street food", "/images/stays/local-cuisine.jpg",
            "Unawatuna Beach", "/images/stays/beach-resort-lawn.jpg",
            "Ayurvedic spa treatment", "/images/stays/deck-infinity-pool.jpg"
    );

    private final HotelService hotelService;
    private final HotelSearchService hotelSearchService;
    private final PromotionService promotionService;
    private final AttractionService attractionService;
    private final AgentService agentService;
    private final ReviewService reviewService;
    private final UserService userService;

    public HomeController(HotelService hotelService, HotelSearchService hotelSearchService,
                          PromotionService promotionService, AttractionService attractionService,
                          AgentService agentService, ReviewService reviewService, UserService userService) {
        this.userService = userService;
        this.hotelService = hotelService;
        this.hotelSearchService = hotelSearchService;
        this.promotionService = promotionService;
        this.attractionService = attractionService;
        this.agentService = agentService;
        this.reviewService = reviewService;
    }

    /** Public landing page - anyone can browse before signing up (PBI-09). */
    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        List<Hotel> approved = hotelService.filterAndSearchHotels(Hotel.APPROVED, null);

        // Featured hotels: best rated first (same ranking as the search page)
        HotelSearchCriteria criteria = new HotelSearchCriteria();
        criteria.setSort("rating");
        List<HotelSearchResult> featured = hotelSearchService.search(criteria).stream().limit(6).collect(Collectors.toList());
        model.addAttribute("featured", featured);

        // Destinations with the number of verified stays in each
        List<Destination> destinations = new ArrayList<>();
        for (int i = 0; i < DESTINATIONS.size(); i++) {
            String[] d = DESTINATIONS.get(i);
            long stays = approved.stream().filter(h -> h.getLocation() != null
                    && h.getLocation().toLowerCase(Locale.ROOT).contains(d[0].toLowerCase(Locale.ROOT))).count();
            destinations.add(new Destination(d[0], d[1], d[2], d[3], stays,
                    Double.parseDouble(d[4]), Double.parseDouble(d[5]), d[6], i < 6));
        }
        model.addAttribute("destinations", destinations);
        Map<String, Destination> byName = destinations.stream().collect(Collectors.toMap(Destination::getName, d -> d));
        model.addAttribute("routeStops", ROUTE.stream().map(byName::get).collect(Collectors.toList()));

        // Live numbers for the stats strip (only real figures are shown)
        long towns = approved.stream().map(h -> h.getLocation() == null ? "" : h.getLocation().split(",")[0].trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty()).distinct().count();
        List<Attraction> attractions = attractionService.all();
        model.addAttribute("hotelCount", approved.size());
        model.addAttribute("townCount", towns);
        model.addAttribute("experienceCount", attractions.size());
        model.addAttribute("agentCount", agentService.getAllApprovedAgents().size());

        // One experience per category - only ones we have a real photo of
        Map<String, Attraction> byCategory = new LinkedHashMap<>();
        for (Attraction a : attractions) {
            if (ATTRACTION_IMAGES.containsKey(a.getName())) byCategory.putIfAbsent(a.getCategory(), a);
        }
        model.addAttribute("experiences", byCategory.values().stream().limit(6).collect(Collectors.toList()));
        model.addAttribute("attractionImages", ATTRACTION_IMAGES);

        // Offers that are running right now
        List<Promotion> offers = new ArrayList<>(promotionService.getActiveCouponsForUser());
        offers.addAll(promotionService.getActiveDiscountsForUser());
        offers.sort(Comparator.comparing((Promotion p) -> p.getDiscountPercentage() == null ? 0 : p.getDiscountPercentage()).reversed());
        List<Promotion> shownOffers = offers.stream().limit(3).collect(Collectors.toList());
        model.addAttribute("offers", shownOffers);
        // When each offer stops (end of the flash window or end date) - the cards count down and retire themselves
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        Map<Long, Long> offerEndsAt = new java.util.HashMap<>();
        for (Promotion p : shownOffers) {
            java.time.LocalDateTime until = promotionService.liveUntil(p, now);
            if (until != null) offerEndsAt.put(p.getId(), until.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        model.addAttribute("offerEndsAt", offerEndsAt);

        // Personal recommendations from the tourist's travel preferences (Phase 13.3)
        AppUser me = authentication == null ? null : userService.findByUsername(authentication.getName()).orElse(null);
        if (me != null && AppUser.ROLE_USER.equals(me.getRole())) {
            model.addAttribute("showPreferencePrompt", !me.hasPreferences());
            if (me.hasPreferences()) {
                String town = me.getPreferredDestination() == null ? null : me.getPreferredDestination().toLowerCase(Locale.ROOT);
                List<HotelSearchResult> recHotels = hotelSearchService.search(criteria).stream()
                        .filter(r -> town == null || (r.getHotel().getLocation() != null
                                && r.getHotel().getLocation().toLowerCase(Locale.ROOT).contains(town)))
                        .filter(r -> me.getBudgetPerNight() == null || (r.getFromPrice() != null && r.getFromPrice() <= me.getBudgetPerNight()))
                        .limit(3).collect(Collectors.toList());
                List<Attraction> recExperiences = attractions.stream()
                        .filter(a -> me.getInterestList().isEmpty() || me.getInterestList().contains(a.getCategory()))
                        .filter(a -> town == null || (a.getCity() != null && a.getCity().toLowerCase(Locale.ROOT).contains(town)))
                        .limit(3).collect(Collectors.toList());
                if (recExperiences.isEmpty() && town != null) {
                    // nothing in that town matches the interests - fall back to the interests anywhere
                    recExperiences = attractions.stream()
                            .filter(a -> me.getInterestList().contains(a.getCategory())).limit(3).collect(Collectors.toList());
                }
                model.addAttribute("recHotels", recHotels);
                model.addAttribute("recExperiences", recExperiences);
                model.addAttribute("me", me);
            }
        }

        // Real guest reviews only
        model.addAttribute("testimonials", reviewService.highlights());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("tomorrow", LocalDate.now().plusDays(1));
        return "home";
    }

    /** Where users land after logging in, depending on their role. */
    /** Public privacy notice, linked from sign-up (consent) and the footer. */
    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        if (hasRole(authentication, "ROLE_ADMIN")) return "redirect:/admin/dashboard";
        if (hasRole(authentication, "ROLE_AGENT")) return "redirect:/agents/portal";
        return "redirect:/hotels";
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }
}
