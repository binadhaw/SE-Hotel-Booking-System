package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.model.Room;
import com.Reservation.Hotel.service.HotelService;
import com.Reservation.Hotel.service.RoomImageStorageService;
import com.Reservation.Hotel.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class RoomController {

    /** Bed types shown in the dropdown. Add new ones here. */
    private static final List<String> BED_TYPES =
            List.of("Single", "Twin", "Double", "Queen", "King", "Bunk", "Sofa");

    private final RoomService roomService;
    private final HotelService hotelService;
    private final RoomImageStorageService imageStorage;

    public RoomController(RoomService roomService, HotelService hotelService,
                          RoomImageStorageService imageStorage) {
        this.roomService = roomService;
        this.hotelService = hotelService;
        this.imageStorage = imageStorage;
    }

    private void prepareForm(Model model, Hotel hotel) {
        model.addAttribute("hotel", hotel);
        model.addAttribute("bedTypes", BED_TYPES);
        model.addAttribute("maxImages", RoomImageStorageService.MAX_IMAGES);
    }

    // ---------------- list ----------------

    @GetMapping("/hotels/{hotelId}/rooms")
    public String listRooms(@PathVariable Long hotelId,
                            @RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
                            Authentication authentication, Model model) {
        Hotel hotel = hotelService.getHotelById(hotelId);
        if (hotel == null) {
            return "redirect:/hotels";
        }
        // Every room is marked reserved / not reserved from the approved bookings
        List<Room> rooms = roomService.getRoomsWithReservationStatus(hotelId);

        // Guests and agents only see rooms they can actually book (available AND not reserved - a room
        // with an approved booking disappears for them until its check-out date has passed).
        // Managers and admins see every room, with its status: Reserved / Available / Unavailable.
        boolean staff = hotelService.canManage(hotel, authentication);
        if (!staff) {
            // Tourists, agents and visitors use the hotel page (rooms + date availability)
            return "redirect:/hotels/" + hotelId + "#rooms";
        }
        if (!staff) {
            rooms = rooms.stream().filter(Room::isBookableByGuests).collect(Collectors.toList());
        } else {
            // Staff summary (counted before filtering) and the All / Available / Reserved filter tabs
            long reservedCount = rooms.stream().filter(Room::isReserved).count();
            long availableCount = rooms.stream().filter(Room::isBookableByGuests).count();
            model.addAttribute("totalRooms", rooms.size());
            model.addAttribute("reservedCount", reservedCount);
            model.addAttribute("availableCount", availableCount);
            model.addAttribute("unavailableCount", rooms.size() - reservedCount - availableCount);

            if ("RESERVED".equalsIgnoreCase(status)) {
                rooms = rooms.stream().filter(Room::isReserved).collect(Collectors.toList());
            } else if ("AVAILABLE".equalsIgnoreCase(status)) {
                rooms = rooms.stream().filter(Room::isBookableByGuests).collect(Collectors.toList());
            } else {
                status = "ALL";
            }
        }
        model.addAttribute("currentStatus", status.toUpperCase());

        model.addAttribute("hotel", hotel);
        model.addAttribute("rooms", rooms);
        model.addAttribute("canManage", staff);
        model.addAttribute("isOwner", hotelService.isOwner(hotel, authentication));
        return "rooms/index";
    }

    // ---------------- create ----------------

    /** Rooms can only be added/changed by the manager who owns the hotel. */
    private boolean denied(Hotel hotel, Authentication authentication, RedirectAttributes redirectAttributes) {
        if (hotelService.isOwner(hotel, authentication) && !Hotel.REMOVED.equals(hotel.getStatus())) return false;
        redirectAttributes.addFlashAttribute("errorMessage", "You can only manage rooms of hotels you own.");
        return true;
    }

    @GetMapping("/hotels/{hotelId}/rooms/new")
    public String showCreateRoomForm(@PathVariable Long hotelId, Authentication authentication, Model model,
                                     RedirectAttributes redirectAttributes) {
        Hotel hotel = hotelService.getHotelById(hotelId);
        if (hotel == null || denied(hotel, authentication, redirectAttributes)) {
            return "redirect:/hotels";
        }
        Room room = new Room();
        room.setHotel(hotel);
        model.addAttribute("room", room);
        prepareForm(model, hotel);
        return "rooms/create";
    }

    @PostMapping("/hotels/{hotelId}/rooms")
    public String saveRoom(@PathVariable Long hotelId,
                           @Valid @ModelAttribute("room") Room room,
                           BindingResult bindingResult,
                           @RequestParam(value = "images", required = false) MultipartFile[] images,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes,
                           Model model) throws IOException {
        Hotel hotel = hotelService.getHotelById(hotelId);
        if (hotel == null || denied(hotel, authentication, redirectAttributes)) {
            return "redirect:/hotels";
        }

        if (roomService.roomNumberTaken(hotelId, room.getRoomNumber(), null)) {
            bindingResult.rejectValue("roomNumber", "duplicate", "This hotel already has a room with that number.");
        }
        if (room.getBedType() != null && !BED_TYPES.contains(room.getBedType())) {
            bindingResult.rejectValue("bedType", "invalid", "Choose a bed type from the list.");
        }

        List<MultipartFile> files = imageStorage.nonEmpty(images);
        String imageError = imageStorage.validate(files, 0);
        if (imageError == null && files.isEmpty()) {
            imageError = "Please upload at least one photo of the room.";
        }

        if (bindingResult.hasErrors() || imageError != null) {
            model.addAttribute("imageError", imageError);
            prepareForm(model, hotel);
            return "rooms/create";
        }

        // Never trust these from the form
        room.setId(null);
        room.setHotel(hotel);
        room.setImagePaths(new ArrayList<>(imageStorage.store(files)));
        roomService.saveRoom(room);

        redirectAttributes.addFlashAttribute("successMessage", "Room added successfully!");
        return "redirect:/hotels/" + hotelId + "/rooms";
    }

    // ---------------- edit ----------------

    @GetMapping("/rooms/{id}/edit")
    public String showEditRoomForm(@PathVariable Long id, Authentication authentication, Model model,
                                   RedirectAttributes redirectAttributes) {
        Room room = roomService.getRoomById(id);
        if (room == null || denied(room.getHotel(), authentication, redirectAttributes)) {
            return "redirect:/hotels";
        }
        model.addAttribute("room", room);
        prepareForm(model, room.getHotel());
        return "rooms/edit";
    }

    @PostMapping("/rooms/{id}/update")
    public String updateRoom(@PathVariable Long id,
                             @Valid @ModelAttribute("room") Room room,
                             BindingResult bindingResult,
                             @RequestParam(value = "removeImages", required = false) List<String> removeImages,
                             @RequestParam(value = "images", required = false) MultipartFile[] images,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes,
                             Model model) throws IOException {
        Room existingRoom = roomService.getRoomById(id);
        if (existingRoom == null || denied(existingRoom.getHotel(), authentication, redirectAttributes)) {
            return "redirect:/hotels";
        }

        if (roomService.roomNumberTaken(existingRoom.getHotel().getId(), room.getRoomNumber(), id)) {
            bindingResult.rejectValue("roomNumber", "duplicate", "This hotel already has a room with that number.");
        }
        if (room.getBedType() != null && !BED_TYPES.contains(room.getBedType())) {
            bindingResult.rejectValue("bedType", "invalid", "Choose a bed type from the list.");
        }

        // Only photos that really belong to this room can be removed
        List<String> currentImages = existingRoom.getImagePaths();
        List<String> toRemove = removeImages == null ? new ArrayList<>()
                : removeImages.stream().filter(currentImages::contains).distinct().collect(Collectors.toList());
        int kept = currentImages.size() - toRemove.size();

        List<MultipartFile> files = imageStorage.nonEmpty(images);
        String imageError = imageStorage.validate(files, kept);
        if (imageError == null && kept + files.size() == 0) {
            imageError = "A room must keep at least one photo.";
        }

        if (bindingResult.hasErrors() || imageError != null) {
            // Re-fill the fields the form doesn't post, so the page renders correctly again
            room.setId(id);
            room.setHotel(existingRoom.getHotel());
            room.setImagePaths(currentImages);
            model.addAttribute("imageError", imageError);
            prepareForm(model, existingRoom.getHotel());
            return "rooms/edit";
        }

        List<String> newPaths = imageStorage.store(files);
        roomService.updateRoom(id, room, toRemove, newPaths);

        redirectAttributes.addFlashAttribute("successMessage", "Room details updated successfully!");
        return "redirect:/hotels/" + existingRoom.getHotel().getId() + "/rooms";
    }

    // ---------------- delete ----------------

    @PostMapping("/rooms/{id}/delete")
    public String deleteRoom(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        Room room = roomService.getRoomById(id);
        if (room == null) return "redirect:/hotels";
        Long hotelId = room.getHotel().getId();
        if (!hotelService.canManage(room.getHotel(), authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can only manage rooms of hotels you own.");
            return "redirect:/hotels";
        }
        String problem = roomService.deleteRoomSafely(id);
        if (problem != null) {
            redirectAttributes.addFlashAttribute("errorMessage", problem);
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Room removed successfully!");
        }
        return "redirect:/hotels/" + hotelId + "/rooms";
    }
}