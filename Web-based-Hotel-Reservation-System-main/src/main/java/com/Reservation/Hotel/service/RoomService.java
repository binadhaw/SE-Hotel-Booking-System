package com.Reservation.Hotel.service;

import com.Reservation.Hotel.model.Room;
import com.Reservation.Hotel.repository.BookingRepository;
import com.Reservation.Hotel.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final RoomImageStorageService imageStorage;

    public RoomService(RoomRepository roomRepository,
                       BookingRepository bookingRepository,
                       RoomImageStorageService imageStorage) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.imageStorage = imageStorage;
    }

    public List<Room> getRoomsByHotelId(Long hotelId) {
        return roomRepository.findByHotelId(hotelId);
    }

    /**
     * Same as getRoomsByHotelId(), but every room is also marked as reserved (or not) from the
     * approved bookings. Use this for any page that has to show or filter by reservation status.
     */
    public List<Room> getRoomsWithReservationStatus(Long hotelId) {
        List<Room> rooms = roomRepository.findByHotelId(hotelId);
        markReservations(rooms, bookingRepository.findActiveReservationsByHotel(hotelId, LocalDate.now()));
        return rooms;
    }

    /** Marks an already-loaded collection of rooms (e.g. hotel.getRooms()) as reserved / not reserved. */
    public void markReservations(Collection<Room> rooms) {
        if (rooms == null || rooms.isEmpty()) return;
        LocalDate today = LocalDate.now();
        List<Booking> active = new ArrayList<>();
        // Rooms of one hotel normally; looping per distinct hotel keeps it correct either way.
        rooms.stream()
                .filter(r -> r.getHotel() != null)
                .map(r -> r.getHotel().getId())
                .distinct()
                .forEach(hotelId -> active.addAll(bookingRepository.findActiveReservationsByHotel(hotelId, today)));
        markReservations(rooms, active);
    }

    private void markReservations(Collection<Room> rooms, List<Booking> activeBookings) {
        // If (unexpectedly) two active bookings hold one room, show the one that ends last.
        Map<Long, Booking> byRoom = new HashMap<>();
        for (Booking b : activeBookings) {
            if (b.getRoom() == null) continue;
            byRoom.merge(b.getRoom().getId(), b,
                    (a, c) -> a.getCheckOutDate().isAfter(c.getCheckOutDate()) ? a : c);
        }
        for (Room r : rooms) {
            Booking b = byRoom.get(r.getId());
            r.setReserved(b != null);
            r.setReservedUntil(b != null ? b.getCheckOutDate() : null);
            r.setReservedBookingId(b != null ? b.getId() : null);
            r.setReservedGuest(b != null ? b.getUsername() : null);
        }
    }

    // ---------------- date-based availability ----------------

    /** Ids of all rooms (any hotel) blocked by an approved booking somewhere in [checkIn, checkOut). */
    public Set<Long> blockedRoomIds(LocalDate checkIn, LocalDate checkOut) {
        Set<Long> blocked = new HashSet<>();
        for (Booking b : bookingRepository.findApprovedOverlapping(checkIn, checkOut)) {
            if (b.getRoom() != null) blocked.add(b.getRoom().getId());
        }
        return blocked;
    }

    /** Rooms of this hotel a guest can book for the given dates (manager left them available, no overlap). */
    public Set<Long> freeRoomIds(Long hotelId, LocalDate checkIn, LocalDate checkOut) {
        Set<Long> blocked = blockedRoomIds(checkIn, checkOut);
        Set<Long> free = new LinkedHashSet<>();
        for (Room r : roomRepository.findByHotelId(hotelId)) {
            if (r.isAvailable() && !blocked.contains(r.getId())) free.add(r.getId());
        }
        return free;
    }

    /**
     * Is the room free for [checkIn, checkOut)? {@code ignoreBookingId} lets a booking being edited
     * or approved ignore itself.
     */
    public boolean isRoomFree(Long roomId, LocalDate checkIn, LocalDate checkOut, Long ignoreBookingId) {
        return findOverlap(roomId, checkIn, checkOut, ignoreBookingId) == null;
    }

    /** The approved booking that clashes with these dates, or null. */
    public Booking findOverlap(Long roomId, LocalDate checkIn, LocalDate checkOut, Long ignoreBookingId) {
        if (roomId == null || checkIn == null || checkOut == null) return null;
        return bookingRepository.findApprovedOverlappingForRoom(roomId, checkIn, checkOut).stream()
                .filter(b -> ignoreBookingId == null || !b.getId().equals(ignoreBookingId))
                .findFirst().orElse(null);
    }

    /** True while an approved booking holds this room (until its check-out date / early release). */
    public boolean isRoomReserved(Long roomId) {
        return roomId != null
                && !bookingRepository.findActiveReservationsByRoom(roomId, LocalDate.now()).isEmpty();
    }

    /** Is another room of this hotel (not {@code exceptRoomId}) already using this number? */
    public boolean roomNumberTaken(Long hotelId, String roomNumber, Long exceptRoomId) {
        if (roomNumber == null) return false;
        return roomRepository.findByHotelId(hotelId).stream()
                .anyMatch(r -> r.getRoomNumber() != null && r.getRoomNumber().trim().equalsIgnoreCase(roomNumber.trim())
                        && (exceptRoomId == null || !r.getId().equals(exceptRoomId)));
    }

    public Room saveRoom(Room room) {
        return roomRepository.save(room);
    }

    public Room getRoomById(Long id) {
        return roomRepository.findById(id).orElse(null);
    }

    /**
     * Deletes a room that has never been booked. A room with booking history is switched to
     * "unavailable" instead, so the guests' booking records stay intact.
     * @return null when deleted, otherwise a message explaining what happened instead
     */
    @Transactional
    public String deleteRoomSafely(Long id) {
        Room room = getRoomById(id);
        if (room == null) return "Room not found.";
        if (bookingRepository.existsByRoomId(id)) {
            room.setAvailable(false);
            roomRepository.save(room);
            return "Room #" + room.getRoomNumber() + " has booking history, so it was marked unavailable instead of being deleted.";
        }
        deleteRoom(id);
        return null;
    }

    /** Deletes the room and then its photo files from disk. */
    @Transactional
    public void deleteRoom(Long id) {
        Room room = getRoomById(id);
        List<String> photos = (room != null && room.getImagePaths() != null)
                ? new ArrayList<>(room.getImagePaths())
                : new ArrayList<>();
        roomRepository.deleteById(id);
        photos.forEach(imageStorage::delete);
    }

    /**
     * Updates the room details, removes the photos in {@code removedImages}
     * (also deleting their files) and appends the photos in {@code addedImages}.
     */
    @Transactional
    public Room updateRoom(Long id, Room updated, List<String> removedImages, List<String> addedImages) {
        Room existing = getRoomById(id);
        if (existing == null) {
            return null;
        }
        existing.setRoomNumber(updated.getRoomNumber());
        existing.setRoomType(updated.getRoomType());
        existing.setPrice(updated.getPrice());
        existing.setAvailable(updated.isAvailable());
        existing.setMaxGuests(updated.getMaxGuests());
        existing.setBedCount(updated.getBedCount());
        existing.setBedType(updated.getBedType());

        if (removedImages != null && !removedImages.isEmpty()) {
            existing.getImagePaths().removeAll(removedImages);
        }
        if (addedImages != null && !addedImages.isEmpty()) {
            existing.getImagePaths().addAll(addedImages);
        }

        Room saved = roomRepository.save(existing);
        if (removedImages != null) {
            removedImages.forEach(imageStorage::delete);
        }
        return saved;
    }
}