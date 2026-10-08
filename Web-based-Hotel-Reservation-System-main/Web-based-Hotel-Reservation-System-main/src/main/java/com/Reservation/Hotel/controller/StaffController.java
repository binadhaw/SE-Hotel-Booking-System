package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.model.StaffMember;
import com.Reservation.Hotel.service.HotelService;
import com.Reservation.Hotel.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/**
 * Hotel staff management (Phase 13.2). The hotel's own manager has full CRUD; administrators can view.
 */
@Controller
public class StaffController {

    private final StaffService staffService;
    private final HotelService hotelService;

    public StaffController(StaffService staffService, HotelService hotelService) {
        this.staffService = staffService;
        this.hotelService = hotelService;
    }

    private String denied(RedirectAttributes ra) {
        ra.addFlashAttribute("errorMessage", "You can only manage staff of hotels you own.");
        return "redirect:/hotels";
    }

    private void prepare(Model model, Hotel hotel, Authentication authentication) {
        model.addAttribute("hotel", hotel);
        model.addAttribute("staffList", staffService.forHotel(hotel.getId()));
        model.addAttribute("roles", StaffMember.ROLES);
        model.addAttribute("shifts", StaffMember.SHIFTS);
        model.addAttribute("isOwner", hotelService.isOwner(hotel, authentication));
    }

    @GetMapping("/hotels/{hotelId}/staff")
    public String list(@PathVariable Long hotelId, Authentication authentication, Model model, RedirectAttributes ra) {
        Hotel hotel = hotelService.getHotelById(hotelId);
        if (!hotelService.canManage(hotel, authentication)) return denied(ra);
        StaffMember blank = new StaffMember();
        blank.setStartDate(LocalDate.now());
        model.addAttribute("staff", blank);
        prepare(model, hotel, authentication);
        return "staff/index";
    }

    @PostMapping("/hotels/{hotelId}/staff")
    public String add(@PathVariable Long hotelId, @Valid @ModelAttribute("staff") StaffMember form, BindingResult bindingResult,
                      Authentication authentication, Model model, RedirectAttributes ra) {
        Hotel hotel = hotelService.getHotelById(hotelId);
        if (!hotelService.isOwner(hotel, authentication) || Hotel.REMOVED.equals(hotel.getStatus())) return denied(ra);
        String problem = staffService.problem(form);
        if (bindingResult.hasErrors() || problem != null) {
            model.addAttribute("errorMessage", problem != null ? problem : "Please correct the highlighted fields.");
            prepare(model, hotel, authentication);
            return "staff/index";
        }
        staffService.add(hotel, form);
        ra.addFlashAttribute("successMessage", form.getFullName() + " added to the team.");
        return "redirect:/hotels/" + hotelId + "/staff";
    }

    @GetMapping("/staff/{id}/edit")
    public String edit(@PathVariable Long id, Authentication authentication, Model model, RedirectAttributes ra) {
        StaffMember staff = staffService.getById(id);
        if (staff == null || !hotelService.isOwner(staff.getHotel(), authentication)) return denied(ra);
        model.addAttribute("staff", staff);
        prepare(model, staff.getHotel(), authentication);
        return "staff/index";
    }

    @PostMapping("/staff/{id}/update")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("staff") StaffMember form, BindingResult bindingResult,
                         Authentication authentication, Model model, RedirectAttributes ra) {
        StaffMember existing = staffService.getById(id);
        if (existing == null || !hotelService.isOwner(existing.getHotel(), authentication)) return denied(ra);
        String problem = staffService.problem(form);
        if (bindingResult.hasErrors() || problem != null) {
            form.setId(id);
            model.addAttribute("errorMessage", problem != null ? problem : "Please correct the highlighted fields.");
            prepare(model, existing.getHotel(), authentication);
            return "staff/index";
        }
        staffService.update(existing, form);
        ra.addFlashAttribute("successMessage", existing.getFullName() + "'s details were updated.");
        return "redirect:/hotels/" + existing.getHotel().getId() + "/staff";
    }

    @PostMapping("/staff/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication, RedirectAttributes ra) {
        StaffMember staff = staffService.getById(id);
        if (staff == null || !hotelService.isOwner(staff.getHotel(), authentication)) return denied(ra);
        Long hotelId = staff.getHotel().getId();
        staffService.delete(staff);
        ra.addFlashAttribute("successMessage", staff.getFullName() + " was removed from the staff list.");
        return "redirect:/hotels/" + hotelId + "/staff";
    }
}
