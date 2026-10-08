package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.model.Attraction;
import com.Reservation.Hotel.service.AttractionService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * PBI-22: travel agents (and hotel managers, for their own hotels) recommend local attractions and
 * experiences. Pinned to a hotel with a distance they appear on that hotel's page.
 */
@Controller
@RequestMapping("/attractions")
public class AttractionController {

    private final AttractionService attractionService;
    private final HotelService hotelService;

    public AttractionController(AttractionService attractionService, HotelService hotelService) {
        this.attractionService = attractionService;
        this.hotelService = hotelService;
    }

    private String back(Authentication authentication, Hotel hotel) {
        if (HotelService.hasRole(authentication, "AGENT")) return "redirect:/agents/portal#experiences";
        return hotel != null ? "redirect:/hotels/" + hotel.getId() + "#nearby" : "redirect:/hotels";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute Attraction attraction, BindingResult bindingResult,
                         @RequestParam(value = "hotelId", required = false) Long hotelId,
                         Authentication authentication, RedirectAttributes redirectAttributes) {
        Hotel hotel = hotelService.getHotelById(hotelId);
        if (hotel != null && !hotel.isApproved()) hotel = null;
        // A manager may only pin attractions to a hotel they own
        if (HotelService.hasRole(authentication, "MANAGER") && !hotelService.isOwner(hotel, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Managers can only add attractions near their own hotels.");
            return back(authentication, hotel);
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return back(authentication, hotel);
        }
        if (!Attraction.CATEGORIES.contains(attraction.getCategory())) attraction.setCategory("Nature");
        attraction.setId(null);
        attraction.setCreatedBy(null);
        attractionService.save(attraction, hotel, authentication.getName());
        redirectAttributes.addFlashAttribute("successMessage", "\"" + attraction.getName() + "\" added to local experiences.");
        return back(authentication, hotel);
    }

    private java.util.List<Hotel> pinnableHotels(Authentication authentication) {
        return HotelService.hasRole(authentication, "MANAGER")
                ? hotelService.filterManagerHotels(authentication.getName(), Hotel.APPROVED, null)
                : hotelService.filterAndSearchHotels(Hotel.APPROVED, null);
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Authentication authentication, Model model,
                               RedirectAttributes redirectAttributes) {
        Attraction a = attractionService.getById(id);
        if (!attractionService.canEdit(a, authentication.getName(), HotelService.hasRole(authentication, "ADMIN"))) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can only edit attractions you added.");
            return back(authentication, a == null ? null : a.getHotel());
        }
        model.addAttribute("attraction", a);
        model.addAttribute("hotels", pinnableHotels(authentication));
        model.addAttribute("categories", Attraction.CATEGORIES);
        return "attractions/edit";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("attraction") Attraction form, BindingResult bindingResult,
                         @RequestParam(value = "hotelId", required = false) Long hotelId,
                         Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        Attraction a = attractionService.getById(id);
        if (!attractionService.canEdit(a, authentication.getName(), HotelService.hasRole(authentication, "ADMIN"))) {
            redirectAttributes.addFlashAttribute("errorMessage", "You can only edit attractions you added.");
            return back(authentication, a == null ? null : a.getHotel());
        }
        Hotel hotel = hotelService.getHotelById(hotelId);
        if (hotel != null && !hotel.isApproved()) hotel = null;
        if (hotel != null && HotelService.hasRole(authentication, "MANAGER") && !hotelService.isOwner(hotel, authentication)) {
            hotel = a.getHotel();
        }
        if (bindingResult.hasErrors()) {
            form.setId(id);
            model.addAttribute("hotels", pinnableHotels(authentication));
            model.addAttribute("categories", Attraction.CATEGORIES);
            return "attractions/edit";
        }
        attractionService.update(a, form, hotel);
        redirectAttributes.addFlashAttribute("successMessage", "\"" + a.getName() + "\" updated.");
        return back(authentication, hotel);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        Attraction a = attractionService.getById(id);
        Hotel hotel = a == null ? null : a.getHotel();
        boolean ok = attractionService.delete(id, authentication.getName(), HotelService.hasRole(authentication, "ADMIN"));
        if (ok) redirectAttributes.addFlashAttribute("successMessage", "Attraction removed.");
        else redirectAttributes.addFlashAttribute("errorMessage", "You can only remove attractions you added.");
        return back(authentication, hotel);
    }
}
