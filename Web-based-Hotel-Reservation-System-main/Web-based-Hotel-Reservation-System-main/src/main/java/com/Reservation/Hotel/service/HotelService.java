package com.Reservation.Hotel.service;

import com.Reservation.Hotel.events.BookingEvent;
import com.Reservation.Hotel.events.HotelEvent;
import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.repository.HotelRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HotelService {

    private final HotelRepository hotelRepository;
    private final BookingRepository bookingRepository;
    private final HotelImageStorageService imageStorage;
    private final RoomImageStorageService roomImageStorage;
    private final ApplicationEventPublisher events;
    private final AttractionService attractionService;
    private final StaffService staffService;

    public HotelService(HotelRepository hotelRepository,
                        BookingRepository bookingRepository,
                        HotelImageStorageService imageStorage,
                        RoomImageStorageService roomImageStorage,
                        ApplicationEventPublisher events,
                        AttractionService attractionService,
                        StaffService staffService) {
        this.staffService = staffService;
        this.attractionService = attractionService;
        this.hotelRepository = hotelRepository;
        this.bookingRepository = bookingRepository;
        this.imageStorage = imageStorage;
        this.roomImageStorage = roomImageStorage;
        this.events = events;
    }

    // ---------------- access rules ----------------

    public static boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    /** Admins manage every hotel; a manager only the hotels they own. */
    public boolean canManage(Hotel hotel, Authentication authentication) {
        if (hotel == null || authentication == null) return false;
        if (hasRole(authentication, "ADMIN")) return true;
        return hasRole(authentication, "MANAGER")
                && authentication.getName().equalsIgnoreCase(hotel.getManagerUsername());
    }

    /** Only the owning manager may change rooms, approve bookings etc. (admins are view/approve-only). */
    public boolean isOwner(Hotel hotel, Authentication authentication) {
        return hotel != null && authentication != null && hasRole(authentication, "MANAGER")
                && authentication.getName().equalsIgnoreCase(hotel.getManagerUsername());
    }

    // ---------------- queries ----------------

    public List<Hotel> getAllHotels() {
        return hotelRepository.findAll();
    }

    public List<Hotel> getHotelsByManager(String managerUsername) {
        return hotelRepository.findByManagerUsername(managerUsername);
    }

    public long countByStatus(String status) {
        return hotelRepository.countByStatus(status);
    }

    /**
     * @param status  ALL / null for every status, otherwise one status
     * @param keyword matched against name and location (case-insensitive), optional
     */
    public List<Hotel> filterAndSearchHotels(String status, String keyword) {
        return filter(hotelRepository.findAll(), status, keyword);
    }

    /** The hotels a manager owns, filtered the same way as filterAndSearchHotels. */
    public List<Hotel> filterManagerHotels(String managerUsername, String status, String keyword) {
        return filter(hotelRepository.findByManagerUsername(managerUsername), status, keyword);
    }

    private List<Hotel> filter(List<Hotel> hotels, String status, String keyword) {
        boolean hasStatus = status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return hotels.stream()
                .filter(h -> hasStatus ? status.equalsIgnoreCase(h.getStatus()) : !Hotel.REMOVED.equals(h.getStatus()))
                .filter(h -> kw.isEmpty()
                        || (h.getName() != null && h.getName().toLowerCase(Locale.ROOT).contains(kw))
                        || (h.getLocation() != null && h.getLocation().toLowerCase(Locale.ROOT).contains(kw)))
                .sorted(Comparator.comparing(Hotel::getId).reversed())
                .collect(Collectors.toList());
    }

    public Hotel getHotelById(Long id) {
        return id == null ? null : hotelRepository.findById(id).orElse(null);
    }

    public Hotel saveHotel(Hotel hotel) {
        if (hotel.getCreatedAt() == null) hotel.setCreatedAt(LocalDateTime.now());
        return hotelRepository.save(hotel);
    }

    // ---------------- admin approval (PBI-13) ----------------

    @Transactional
    public void approve(Hotel hotel, String actor) {
        hotel.setStatus(Hotel.APPROVED);
        hotel.setStatusNote(null);
        hotelRepository.save(hotel);
        events.publishEvent(new HotelEvent(hotel, HotelEvent.Type.APPROVED, actor, null));
    }

    @Transactional
    public void reject(Hotel hotel, String reason, String actor) {
        hotel.setStatus(Hotel.REJECTED);
        hotel.setStatusNote(reason == null || reason.isBlank() ? "Rejected by the administrator." : reason.trim());
        hotelRepository.save(hotel);
        events.publishEvent(new HotelEvent(hotel, HotelEvent.Type.REJECTED, actor, hotel.getStatusNote()));
    }

    /** A rejected hotel that the manager has corrected goes back into the approval queue. */
    @Transactional
    public void resubmit(Hotel hotel) {
        hotel.setStatus(Hotel.PENDING);
        hotel.setStatusNote(null);
        hotelRepository.save(hotel);
    }

    // ---------------- removal (PBI-16) ----------------

    /**
     * Takes a hotel off the platform without destroying booking records.
     * Hotels that never had a booking are deleted outright (with their photos);
     * otherwise the hotel is marked REMOVED and every still-open booking is cancelled -
     * paid ones go through the normal refund pipeline - and the guests are notified.
     *
     * @return a summary message for the person who removed it
     */
    @Transactional
    public String removeHotel(Long id, String reason, String actor) {
        Hotel hotel = getHotelById(id);
        if (hotel == null) return "Hotel not found.";

        List<Booking> bookings = bookingRepository.findByHotelId(id);
        if (bookings.isEmpty()) {
            events.publishEvent(new HotelEvent(hotel, HotelEvent.Type.REMOVED, actor, "Deleted (no booking history)"));
            deleteHotel(id);
            return "Hotel deleted.";
        }

        String why = (reason == null || reason.isBlank()) ? "The hotel has been removed from the platform." : reason.trim();
        int cancelled = 0, refunds = 0;
        LocalDate today = LocalDate.now();
        for (Booking b : bookings) {
            boolean open = "PENDING".equals(b.getStatus())
                    || ("APPROVED".equals(b.getStatus()) && b.getCheckOutDate() != null && !b.getCheckOutDate().isBefore(today));
            if (!open) continue;
            if (b.getAmountPaid() > 0.0) {
                b.setStatus("REFUND_PENDING");
                b.setBalanceDue(-b.getAmountPaid());
                b.setRefundId("REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                b.setStatusNote(String.format("Cancelled: %s A refund of $%.2f is due.", why, b.getAmountPaid()));
                refunds++;
            } else {
                b.setStatus("CANCELLED");
                b.setBalanceDue(0.0);
                b.setStatusNote("Cancelled: " + why);
            }
            bookingRepository.save(b);
            cancelled++;
            events.publishEvent(new BookingEvent(b, BookingEvent.Type.CANCELLED_BY_HOTEL, actor, why));
        }

        hotel.setStatus(Hotel.REMOVED);
        hotel.setStatusNote(why);
        hotelRepository.save(hotel);
        String summary = String.format("%d open booking(s) cancelled, %d refund(s) pending.", cancelled, refunds);
        events.publishEvent(new HotelEvent(hotel, HotelEvent.Type.REMOVED, actor, why + " - " + summary));

        return String.format("Hotel removed from the platform. %d open booking(s) cancelled, %d refund(s) pending. "
                + "Booking history has been kept.", cancelled, refunds);
    }

    /** Hard delete of the hotel, its rooms and photo files. Only used for hotels without bookings. */
    @Transactional
    public void deleteHotel(Long id) {
        Hotel hotel = getHotelById(id);
        List<String> photos = (hotel != null && hotel.getImagePaths() != null)
                ? new ArrayList<>(hotel.getImagePaths())
                : new ArrayList<>();
        List<String> roomPhotos = new ArrayList<>();
        if (hotel != null && hotel.getRooms() != null) {
            hotel.getRooms().forEach(r -> {
                if (r.getImagePaths() != null) roomPhotos.addAll(r.getImagePaths());
            });
        }
        attractionService.detachFromHotel(id);
        staffService.deleteAllForHotel(id);
        hotelRepository.deleteById(id);
        photos.forEach(imageStorage::delete);
        roomPhotos.forEach(roomImageStorage::delete);
    }

    // ---------------- update ----------------

    /**
     * Updates the text fields and amenities, removes the photos in {@code removedImages}
     * (also deleting their files) and appends the photos in {@code addedImages}.
     */
    @Transactional
    public Hotel updateHotel(Long id, Hotel updatedHotel, List<String> removedImages, List<String> addedImages) {
        Hotel existingHotel = getHotelById(id);
        if (existingHotel == null) {
            return null;
        }
        existingHotel.setName(updatedHotel.getName());
        existingHotel.setLocation(updatedHotel.getLocation());
        existingHotel.setDescription(updatedHotel.getDescription());
        existingHotel.setAmenities(updatedHotel.getAmenities());
        existingHotel.setContactPhone(updatedHotel.getContactPhone());
        existingHotel.setContactEmail(updatedHotel.getContactEmail());

        if (removedImages != null && !removedImages.isEmpty()) {
            existingHotel.getImagePaths().removeAll(removedImages);
        }
        if (addedImages != null && !addedImages.isEmpty()) {
            existingHotel.getImagePaths().addAll(addedImages);
        }

        Hotel saved = hotelRepository.save(existingHotel);
        if (removedImages != null) {
            removedImages.forEach(imageStorage::delete);
        }
        return saved;
    }

    /** Hotels created before ownership existed are given to the default manager account. */
    @Transactional
    public int assignUnownedHotels(String managerUsername) {
        List<Hotel> unowned = hotelRepository.findByManagerUsernameIsNull();
        unowned.forEach(h -> {
            h.setManagerUsername(managerUsername);
            if (h.getCreatedAt() == null) h.setCreatedAt(LocalDateTime.now());
        });
        hotelRepository.saveAll(unowned);
        return unowned.size();
    }
}
