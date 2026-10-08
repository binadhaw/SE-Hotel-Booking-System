package com.Reservation.Hotel.service;

import com.Reservation.Hotel.dto.HotelSearchCriteria;
import com.Reservation.Hotel.dto.HotelSearchResult;
import com.Reservation.Hotel.dto.RatingStats;
import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.model.Room;
import com.Reservation.Hotel.repository.HotelRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tourist hotel search over APPROVED hotels: keyword (name/location), nightly price range,
 * required facilities, and - when dates are given - only rooms that are free for the whole stay.
 * With a party size, the free rooms together must sleep that many guests (multi-room families, PBI-06).
 */
@Service
public class HotelSearchService {

    private final HotelRepository hotelRepository;
    private final RoomService roomService;
    private final RatingProvider ratingProvider;

    public HotelSearchService(HotelRepository hotelRepository, RoomService roomService, RatingProvider ratingProvider) {
        this.hotelRepository = hotelRepository;
        this.roomService = roomService;
        this.ratingProvider = ratingProvider;
    }

    public List<HotelSearchResult> search(HotelSearchCriteria c) {
        String kw = c.getKeyword() == null ? "" : c.getKeyword().trim().toLowerCase(Locale.ROOT);
        Set<Long> blocked = c.hasDates() ? roomService.blockedRoomIds(c.getCheckIn(), c.getCheckOut()) : Set.of();
        Map<Long, RatingStats> ratings = ratingProvider.allHotelRatings();
        RatingStats unrated = new RatingStats(0.0, 0);

        List<HotelSearchResult> results = new ArrayList<>();
        for (Hotel h : hotelRepository.findByStatus(Hotel.APPROVED)) {
            if (!kw.isEmpty() && !contains(h.getName(), kw) && !contains(h.getLocation(), kw)) continue;
            if (c.getAmenities() != null && !c.getAmenities().isEmpty()
                    && !new HashSet<>(h.getAmenityList()).containsAll(c.getAmenities())) continue;

            List<Room> rooms = (h.getRooms() == null ? List.<Room>of() : h.getRooms()).stream()
                    .filter(Room::isAvailable)
                    .filter(r -> r.getPrice() != null)
                    .filter(r -> c.getMinPrice() == null || r.getPrice() >= c.getMinPrice())
                    .filter(r -> c.getMaxPrice() == null || r.getPrice() <= c.getMaxPrice())
                    .filter(r -> !blocked.contains(r.getId()))
                    .collect(Collectors.toList());

            boolean roomFilter = c.getMinPrice() != null || c.getMaxPrice() != null || c.hasDates() || c.getGuests() != null;
            if (roomFilter && rooms.isEmpty()) continue;

            int sleeps = rooms.stream().mapToInt(r -> r.getMaxGuests() == null ? 0 : r.getMaxGuests()).sum();
            if (c.getGuests() != null && c.getGuests() > 0 && sleeps < c.getGuests()) continue;

            Double from = rooms.stream().map(Room::getPrice).min(Double::compare).orElse(null);
            HotelSearchResult result = new HotelSearchResult(h, from, rooms.size(), sleeps);
            RatingStats rating = ratings.getOrDefault(h.getId(), unrated);
            result.setAverageRating(rating.getAverageRating());
            result.setReviewCount(rating.getReviewCount());
            results.add(result);
        }

        Comparator<HotelSearchResult> byPrice = Comparator.comparing(
                r -> r.getFromPrice() == null ? Double.MAX_VALUE : r.getFromPrice());
        switch (c.getSort() == null ? "" : c.getSort()) {
            case "price_asc" -> results.sort(byPrice);
            case "price_desc" -> results.sort(Comparator.comparing(
                    (HotelSearchResult r) -> r.getFromPrice() == null ? -1.0 : r.getFromPrice()).reversed());
            case "rating" -> results.sort(Comparator.comparing(HotelSearchResult::getAverageRating).reversed()
                    .thenComparing(Comparator.comparing(HotelSearchResult::getReviewCount).reversed()));
            case "name" -> results.sort(Comparator.comparing(r -> r.getHotel().getName().toLowerCase(Locale.ROOT)));
            default -> results.sort(Comparator.comparing(HotelSearchResult::getAverageRating).reversed()
                    .thenComparing(byPrice));
        }
        return results;
    }

    private boolean contains(String text, String kw) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(kw);
    }
}
