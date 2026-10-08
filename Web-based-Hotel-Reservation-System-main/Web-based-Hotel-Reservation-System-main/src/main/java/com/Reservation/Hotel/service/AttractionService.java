package com.Reservation.Hotel.service;

import com.Reservation.Hotel.model.Attraction;
import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.repository.AttractionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Local attractions and experiences recommended by travel agents (and hotel managers). */
@Service
public class AttractionService {

    private final AttractionRepository attractionRepository;

    public AttractionService(AttractionRepository attractionRepository) {
        this.attractionRepository = attractionRepository;
    }

    public List<Attraction> all() {
        return attractionRepository.findAllByOrderByCityAscNameAsc();
    }

    public List<Attraction> byCreator(String username) {
        return attractionRepository.findByCreatedByOrderByCreatedAtDesc(username);
    }

    public Attraction getById(Long id) {
        return id == null ? null : attractionRepository.findById(id).orElse(null);
    }

    public List<Attraction> findAllById(List<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of() : attractionRepository.findAllById(ids);
    }

    /**
     * What is near a hotel (PBI-03): attractions pinned to this hotel, plus attractions in the same town
     * that are not pinned to some other hotel. Pinned ones (with a distance) first, nearest first,
     * then the cheapest.
     */
    public List<Attraction> nearHotel(Hotel hotel) {
        String location = hotel.getLocation() == null ? "" : hotel.getLocation().toLowerCase(Locale.ROOT);
        return attractionRepository.findAll().stream()
                .filter(a -> (a.getHotel() != null && a.getHotel().getId().equals(hotel.getId()))
                        || (a.getHotel() == null && a.getCity() != null
                            && location.contains(a.getCity().toLowerCase(Locale.ROOT))))
                .sorted(Comparator.comparing((Attraction a) -> a.getDistanceKm() == null ? Double.MAX_VALUE : a.getDistanceKm())
                        .thenComparing(a -> a.getEstimatedCost() == null ? 0.0 : a.getEstimatedCost()))
                .collect(Collectors.toList());
    }

    @Transactional
    public Attraction save(Attraction attraction, Hotel hotel, String createdBy) {
        attraction.setHotel(hotel);
        if (hotel == null) attraction.setDistanceKm(null);
        if (attraction.getCreatedBy() == null) attraction.setCreatedBy(createdBy);
        return attractionRepository.save(attraction);
    }

    public boolean canEdit(Attraction a, String username, boolean admin) {
        return a != null && (admin || username.equals(a.getCreatedBy()));
    }

    /** Copies the editable fields from the form. */
    @Transactional
    public void update(Attraction existing, Attraction form, Hotel hotel) {
        existing.setName(form.getName().trim());
        existing.setCategory(Attraction.CATEGORIES.contains(form.getCategory()) ? form.getCategory() : existing.getCategory());
        existing.setCity(form.getCity().trim());
        existing.setDescription(form.getDescription());
        existing.setEstimatedCost(form.getEstimatedCost());
        existing.setHotel(hotel);
        existing.setDistanceKm(hotel == null ? null : form.getDistanceKm());
        attractionRepository.save(existing);
    }

    /** Creator or an administrator may delete. @return false when not allowed */
    @Transactional
    public boolean delete(Long id, String username, boolean admin) {
        Attraction a = getById(id);
        if (a == null || (!admin && !username.equals(a.getCreatedBy()))) return false;
        attractionRepository.unlinkFromItineraries(id);
        attractionRepository.delete(a);
        return true;
    }

    @Transactional
    public void detachFromHotel(Long hotelId) {
        attractionRepository.detachFromHotel(hotelId);
    }

    /** A starter set of well-known Sri Lankan attractions, added once to an empty table. */
    @Transactional
    public void seedIfEmpty() {
        if (attractionRepository.count() > 0) return;
        attractionRepository.saveAll(List.of(
                new Attraction("Temple of the Sacred Tooth Relic", "Culture & Heritage", "Kandy", "UNESCO-listed temple housing the relic of the tooth of the Buddha.", 15),
                new Attraction("Royal Botanical Gardens, Peradeniya", "Nature", "Kandy", "147 acres of orchids, spice gardens and giant bamboo.", 10),
                new Attraction("Kandy Lake walk", "Nature", "Kandy", "Free lakeside stroll in the heart of the city.", 0),
                new Attraction("Galle Fort ramparts", "Culture & Heritage", "Galle", "Walk the 17th-century Dutch fort walls at sunset.", 0),
                new Attraction("Unawatuna Beach", "Beach", "Galle", "Sheltered bay ideal for swimming and snorkelling.", 0),
                new Attraction("Nine Arch Bridge", "Nature", "Ella", "Iconic colonial-era railway viaduct in the tea hills.", 0),
                new Attraction("Little Adam's Peak hike", "Adventure", "Ella", "Easy 45-minute hike with panoramic views.", 0),
                new Attraction("Sigiriya Rock Fortress", "Culture & Heritage", "Sigiriya", "5th-century rock citadel with frescoes and mirror wall.", 30),
                new Attraction("Pidurangala Rock", "Adventure", "Sigiriya", "Sunrise climb with the best view of Sigiriya.", 3),
                new Attraction("Gangaramaya Temple", "Culture & Heritage", "Colombo", "Eclectic Buddhist temple and museum.", 3),
                new Attraction("Galle Face Green street food", "Food & Drink", "Colombo", "Ocean-side promenade famous for evening street food.", 5),
                new Attraction("Whale watching", "Wildlife", "Mirissa", "Morning boat trip to see blue whales and dolphins (Nov-Apr).", 50),
                new Attraction("Coconut Tree Hill", "Beach", "Mirissa", "Palm-covered headland, a classic photo spot.", 0),
                new Attraction("Yala National Park safari", "Wildlife", "Yala", "Jeep safari - home of the highest density of leopards.", 60),
                new Attraction("Pedro Tea Estate tour", "Food & Drink", "Nuwara Eliya", "Factory tour and tasting in the hill country.", 5),
                new Attraction("Gregory Lake", "Nature", "Nuwara Eliya", "Boating and lakeside picnics.", 2),
                new Attraction("Negombo fish market", "Shopping", "Negombo", "Bustling early-morning Lellama fish market.", 0),
                new Attraction("Ayurvedic spa treatment", "Wellness", "Bentota", "Traditional herbal massage and therapies.", 35)
        ));
    }
}
