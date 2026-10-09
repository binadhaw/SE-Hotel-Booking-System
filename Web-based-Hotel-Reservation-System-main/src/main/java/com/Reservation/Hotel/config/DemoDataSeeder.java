package com.Reservation.Hotel.config;

import com.Reservation.Hotel.model.Room;
import com.Reservation.Hotel.repository.RoomRepository;
import com.Reservation.Hotel.service.RoomImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Demo hotels for presentations: one or more approved properties in every town on the home page island map,
 * owned by the demo "manager" account, with rooms and photos (copied from static/images/stays into ./uploads).
 * Each hotel also gets a small demo staff team. A hotel is only created when no hotel with the same name
 * exists, so restarts never duplicate them; disable with app.demo-data=false (DEMO_DATA=false).
 * No reviews or bookings are invented - those come from real use of the system.
 */
@Component
@Order(2)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private record RoomSpec(String number, String type, double price, int guests, int beds, String bedType, String photo) {}

    private record HotelSpec(String name, String location, String description, String amenities, String phone,
                             List<String> photos, List<RoomSpec> rooms) {}

    private static final List<HotelSpec> HOTELS = List.of(
            new HotelSpec("Lagoon Breeze Villa", "Galle",
                    "Colonial-style villa a short walk from Galle Fort, with an ocean-facing pool and sunset terrace.",
                    "Free WiFi,Swimming Pool,Beach Access,Restaurant,Air Conditioning,24/7 Front Desk", "+94 91 222 3344",
                    List.of("sunset-pool", "white-villa", "balcony-ocean-view"),
                    List.of(new RoomSpec("101", "Deluxe Ocean View", 140, 2, 1, "King", "balcony-ocean-view"),
                            new RoomSpec("102", "Garden Twin", 110, 2, 2, "Single", "white-villa"),
                            new RoomSpec("201", "Family Suite", 220, 4, 2, "Queen", "sunset-pool"))),
            new HotelSpec("Palm Cove Resort", "Mirissa",
                    "Beachfront resort on Mirissa bay - whale-watching trips, cabanas on the sand and a spa.",
                    "Free WiFi,Swimming Pool,Spa & Wellness,Rooftop Bar,Beach Access,Room Service", "+94 41 225 6677",
                    List.of("beach-deck", "palm-pool-villa", "beach-resort-lawn"),
                    List.of(new RoomSpec("11", "Beach Cabana", 120, 2, 1, "Queen", "beach-deck"),
                            new RoomSpec("12", "Pool Villa", 190, 3, 1, "King", "palm-pool-villa"),
                            new RoomSpec("21", "Family Bungalow", 210, 5, 3, "Double", "beach-resort-lawn"))),
            new HotelSpec("Misty Hills Retreat", "Ella",
                    "Tea-country hideaway with valley views, hiking trails to Little Adam's Peak and home-cooked curries.",
                    "Free WiFi,Restaurant,Garden,Free Parking,Laundry Service", "+94 57 222 8899",
                    List.of("hillside-terrace", "infinity-pool-sunset"),
                    List.of(new RoomSpec("A1", "Valley View Double", 95, 2, 1, "Double", "hillside-terrace"),
                            new RoomSpec("A2", "Infinity Pool Suite", 175, 2, 1, "King", "infinity-pool-sunset"))),
            new HotelSpec("Lion Rock Lodge", "Sigiriya",
                    "Jungle lodge with a pool facing the Sigiriya rock fortress; safaris and village tours arranged.",
                    "Free WiFi,Swimming Pool,Airport Shuttle,Restaurant,Garden,Concierge", "+94 66 228 1122",
                    List.of("rock-view-pool", "resort-garden-pool"),
                    List.of(new RoomSpec("L1", "Rock View Chalet", 110, 2, 1, "Queen", "rock-view-pool"),
                            new RoomSpec("L2", "Garden Family Room", 160, 4, 2, "Double", "resort-garden-pool"))),
            new HotelSpec("Grand Tea Country Hotel", "Nuwara Eliya",
                    "Heritage hotel in 'Little England' - fireplaces, high tea on the lawn and Gregory Lake nearby.",
                    "Free WiFi,Restaurant,Room Service,Garden,Conference Hall,Free Parking", "+94 52 222 4455",
                    List.of("hill-country-hotel", "lakeside-dining"),
                    List.of(new RoomSpec("301", "Heritage Double", 160, 2, 1, "Double", "hill-country-hotel"),
                            new RoomSpec("302", "Colonial Suite", 240, 3, 1, "King", "lakeside-dining"))),
            new HotelSpec("Surf Point Cabanas", "Arugam Bay",
                    "Laid-back cabanas steps from the point break, with surf lessons, board hire and a deck pool.",
                    "Free WiFi,Beach Access,Free Parking,Swimming Pool", "+94 63 224 7788",
                    List.of("surf-beach", "deck-infinity-pool", "ocean-infinity-pool"),
                    List.of(new RoomSpec("S1", "Surf Cabana", 70, 2, 1, "Double", "surf-beach"),
                            new RoomSpec("S2", "Ocean Deck Room", 105, 2, 1, "King", "deck-infinity-pool"))),
            new HotelSpec("Galle Face Skyline Hotel", "Colombo",
                    "City hotel steps from Galle Face Green, with a rooftop pool and sunset bar over the Indian Ocean.",
                    "Free WiFi,Swimming Pool,Rooftop Bar,Restaurant,Air Conditioning,24/7 Front Desk", "+94 11 245 6789",
                    List.of("night-pool-villa", "lakeside-dining"),
                    List.of(new RoomSpec("1201", "City View King", 150, 2, 1, "King", "night-pool-villa"),
                            new RoomSpec("1405", "Ocean Suite", 260, 3, 1, "King", "lakeside-dining"))),
            new HotelSpec("Lellama Beach Hotel", "Negombo",
                    "Easy-going beach hotel 20 minutes from the airport - ideal for your first and last night.",
                    "Free WiFi,Swimming Pool,Beach Access,Airport Shuttle,Restaurant", "+94 31 222 1144",
                    List.of("beach-resort-lawn", "palm-pool-villa"),
                    List.of(new RoomSpec("N1", "Garden Double", 80, 2, 1, "Double", "beach-resort-lawn"),
                            new RoomSpec("N2", "Beachfront Family Room", 130, 4, 2, "Queen", "palm-pool-villa"))),
            new HotelSpec("Elephant Rock Residence", "Kurunegala",
                    "Quiet colonial bungalow beneath Elephant Rock, a calm stop between Colombo and the ancient cities.",
                    "Free WiFi,Garden,Free Parking,Restaurant", "+94 37 222 5566",
                    List.of("white-villa", "resort-garden-pool"),
                    List.of(new RoomSpec("K1", "Bungalow Double", 60, 2, 1, "Double", "white-villa"),
                            new RoomSpec("K2", "Garden Twin", 70, 2, 2, "Twin", "resort-garden-pool"))),
            new HotelSpec("Golden Cave Retreat", "Dambulla",
                    "Jungle retreat a short drive from the Dambulla cave temples and Sigiriya, with a pool among the trees.",
                    "Free WiFi,Swimming Pool,Restaurant,Garden,Free Parking", "+94 66 228 4433",
                    List.of("hillside-terrace", "resort-garden-pool"),
                    List.of(new RoomSpec("D1", "Jungle View Double", 85, 2, 1, "Queen", "hillside-terrace"),
                            new RoomSpec("D2", "Family Chalet", 135, 4, 2, "Double", "resort-garden-pool"))),
            new HotelSpec("Sacred City Heritage Lodge", "Anuradhapura",
                    "Peaceful lodge near the ancient stupas - bicycles provided to explore the sacred city.",
                    "Free WiFi,Swimming Pool,Garden,Restaurant,Free Parking", "+94 25 222 7788",
                    List.of("resort-garden-pool", "palm-pool-villa"),
                    List.of(new RoomSpec("A1", "Heritage Double", 80, 2, 1, "Double", "resort-garden-pool"),
                            new RoomSpec("A2", "Pool Villa", 140, 3, 1, "King", "palm-pool-villa"))),
            new HotelSpec("Baobab Bay Guesthouse", "Mannar",
                    "Small family-run guesthouse near the old baobab tree and the flamingo lagoons of Mannar island.",
                    "Free WiFi,Beach Access,Free Parking,Restaurant", "+94 23 222 3311",
                    List.of("beach-deck", "white-villa"),
                    List.of(new RoomSpec("M1", "Lagoon Double", 55, 2, 1, "Double", "beach-deck"),
                            new RoomSpec("M2", "Family Room", 90, 4, 2, "Queen", "white-villa"))),
            new HotelSpec("Palmyrah Lagoon House", "Jaffna",
                    "Restored Jaffna villa with palmyrah gardens, home-cooked Jaffna crab curry and trips to the islands.",
                    "Free WiFi,Garden,Restaurant,Air Conditioning,Free Parking", "+94 21 222 4567",
                    List.of("white-villa", "ocean-infinity-pool"),
                    List.of(new RoomSpec("J1", "Courtyard Double", 75, 2, 1, "Double", "white-villa"),
                            new RoomSpec("J2", "Lagoon Family Suite", 120, 4, 2, "Queen", "ocean-infinity-pool"))),
            new HotelSpec("Northern Gateway Inn", "Vavuniya",
                    "Comfortable stopover on the road north, with a quiet garden and a hearty Sri Lankan breakfast.",
                    "Free WiFi,Garden,Free Parking,Restaurant", "+94 24 222 1100",
                    List.of("resort-garden-pool"),
                    List.of(new RoomSpec("V1", "Standard Double", 45, 2, 1, "Double", "resort-garden-pool"),
                            new RoomSpec("V2", "Family Room", 70, 4, 2, "Twin", "resort-garden-pool"))),
            new HotelSpec("Nilaveli Blue Beach Resort", "Trincomalee",
                    "Beach resort on the white sands of Nilaveli - snorkel at Pigeon Island and watch for whales.",
                    "Free WiFi,Swimming Pool,Beach Access,Restaurant,Spa & Wellness", "+94 26 223 9900",
                    List.of("ocean-infinity-pool", "beach-resort-lawn"),
                    List.of(new RoomSpec("T1", "Sea View Double", 110, 2, 1, "King", "ocean-infinity-pool"),
                            new RoomSpec("T2", "Beach Villa", 180, 4, 2, "Queen", "beach-resort-lawn"))),
            new HotelSpec("Lagoon Lighthouse Retreat", "Batticaloa",
                    "Lagoon-side retreat near the Dutch fort and the lighthouse, with canoe trips at sunrise.",
                    "Free WiFi,Swimming Pool,Garden,Restaurant", "+94 65 222 6655",
                    List.of("sunset-pool", "beach-deck"),
                    List.of(new RoomSpec("B1", "Lagoon Double", 70, 2, 1, "Double", "sunset-pool"),
                            new RoomSpec("B2", "Lighthouse Suite", 115, 3, 1, "King", "beach-deck"))),
            new HotelSpec("Lake Parakrama Villas", "Polonnaruwa",
                    "Villas on the shore of the ancient Parakrama Samudra reservoir, close to the royal ruins.",
                    "Free WiFi,Swimming Pool,Restaurant,Garden,Free Parking", "+94 27 222 3344",
                    List.of("lakeside-dining", "resort-garden-pool"),
                    List.of(new RoomSpec("P1", "Lake View Double", 90, 2, 1, "Queen", "lakeside-dining"),
                            new RoomSpec("P2", "Family Villa", 150, 4, 2, "Double", "resort-garden-pool"))),
            new HotelSpec("Temple Lake Grand Kandy", "Kandy",
                    "Grand hill-country hotel overlooking Kandy Lake and the Temple of the Tooth.",
                    "Free WiFi,Swimming Pool,Restaurant,Spa & Wellness,Room Service,Free Parking", "+94 81 222 7766",
                    List.of("hill-country-hotel", "hillside-terrace"),
                    List.of(new RoomSpec("201", "Lake View Double", 120, 2, 1, "Double", "hill-country-hotel"),
                            new RoomSpec("305", "Royal Suite", 200, 3, 1, "King", "hillside-terrace"))),
            new HotelSpec("Leopard Trails Safari Lodge", "Yala",
                    "Lodge on the edge of Yala National Park - dawn jeep safaris to look for leopards and elephants.",
                    "Free WiFi,Swimming Pool,Restaurant,Free Parking,Concierge", "+94 47 223 8811",
                    List.of("resort-garden-pool", "hillside-terrace"),
                    List.of(new RoomSpec("Y1", "Safari Chalet", 130, 2, 1, "Queen", "resort-garden-pool"),
                            new RoomSpec("Y2", "Family Safari Tent", 210, 4, 2, "Double", "hillside-terrace"))),
            new HotelSpec("Southern Dunes Resort", "Hambantota",
                    "Relaxed resort by the dunes, close to the Bundala bird sanctuary and quiet golden beaches.",
                    "Free WiFi,Swimming Pool,Beach Access,Restaurant", "+94 47 222 0099",
                    List.of("sunset-pool", "beach-deck"),
                    List.of(new RoomSpec("H1", "Dune View Double", 95, 2, 1, "Double", "sunset-pool"),
                            new RoomSpec("H2", "Beach Suite", 150, 3, 1, "King", "beach-deck"))),
            new HotelSpec("Bentota River & Sea Resort", "Bentota",
                    "Between the Bentota river and the sea - river safaris, the turtle hatchery and Ayurveda spa.",
                    "Free WiFi,Swimming Pool,Beach Access,Spa & Wellness,Restaurant", "+94 34 227 5544",
                    List.of("deck-infinity-pool", "palm-pool-villa"),
                    List.of(new RoomSpec("R1", "River View Double", 140, 2, 1, "King", "deck-infinity-pool"),
                            new RoomSpec("R2", "Ocean Pool Villa", 230, 4, 2, "Queen", "palm-pool-villa"))),
            new HotelSpec("Gem City Rainforest Lodge", "Ratnapura",
                    "Lodge in the city of gems, a gateway to Sinharaja rainforest and the Adam's Peak trail.",
                    "Free WiFi,Garden,Restaurant,Free Parking", "+94 45 222 6677",
                    List.of("hillside-terrace", "resort-garden-pool"),
                    List.of(new RoomSpec("G1", "Forest View Double", 65, 2, 1, "Double", "hillside-terrace"),
                            new RoomSpec("G2", "Family Room", 105, 4, 2, "Twin", "resort-garden-pool")))
    );

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final StaffRepository staffRepository;
    private final HotelImageStorageService hotelImages;
    private final RoomImageStorageService roomImages;

    @Value("${app.demo-data:true}")
    private boolean enabled;

    public DemoDataSeeder(HotelRepository hotelRepository, RoomRepository roomRepository, StaffRepository staffRepository,
                          HotelImageStorageService hotelImages, RoomImageStorageService roomImages) {
        this.staffRepository = staffRepository;
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.hotelImages = hotelImages;
        this.roomImages = roomImages;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) return;
        Set<String> existing = hotelRepository.findAll().stream().map(Hotel::getName).collect(Collectors.toSet());
        int created = 0;
        try {
            for (HotelSpec spec : HOTELS) {
                if (existing.contains(spec.name())) continue;   // already there (or removed by an admin - keep it that way)
                Hotel hotel = new Hotel(spec.name(), spec.location(), Hotel.APPROVED, spec.description(), spec.amenities());
                hotel.setManagerUsername("manager");
                hotel.setContactPhone(spec.phone());
                hotel.setContactEmail("reservations@" + spec.name().toLowerCase().replaceAll("[^a-z]", "") + ".lk");
                hotel.setCreatedAt(LocalDateTime.now());
                List<String> paths = new ArrayList<>();
                for (String photo : spec.photos()) paths.add(hotelImages.storeJpeg(photo(photo)));
                hotel.setImagePaths(paths);
                hotelRepository.save(hotel);

                for (RoomSpec r : spec.rooms()) {
                    Room room = new Room(r.number(), r.type(), r.price(), true, hotel);
                    room.setMaxGuests(r.guests());
                    room.setBedCount(r.beds());
                    room.setBedType(r.bedType());
                    room.setImagePaths(new ArrayList<>(List.of(roomImages.storeJpeg(photo(r.photo())))));
                    roomRepository.save(room);
                }
                seedStaff(hotel, spec.name());
                created++;
            }
            if (created > 0) log.info("Demo data: created {} hotels (set DEMO_DATA=false to disable)", created);
        } catch (IOException e) {
            log.warn("Demo data could not be created: {}", e.getMessage());
        }
    }

    /** A small demo team per hotel so the staff-management page has content. */
    private void seedStaff(Hotel hotel, String hotelName) {
        String domain = hotelName.toLowerCase().replaceAll("[^a-z]", "") + ".lk";
        String[][] team = {
                {"Front Desk", "MORNING", "Kasun Perera"},
                {"Chef", "EVENING", "Dilani Fernando"},
                {"Housekeeping", "ROTATING", "Saman Kumara"}
        };
        int i = 0;
        for (String[] t : team) {
            StaffMember s = new StaffMember();
            s.setHotel(hotel);
            s.setRole(t[0]);
            s.setShift(t[1]);
            s.setFullName(t[2]);
            s.setEmail(t[2].split(" ")[0].toLowerCase() + "@" + domain);
            s.setStartDate(LocalDate.now().minusMonths(6L + 7L * i++));
            s.setActive(true);
            staffRepository.save(s);
        }
    }

    private static byte[] photo(String name) throws IOException {
        try (InputStream in = new ClassPathResource("static/images/stays/" + name + ".jpg").getInputStream()) {
            return in.readAllBytes();
        }
    }
}
