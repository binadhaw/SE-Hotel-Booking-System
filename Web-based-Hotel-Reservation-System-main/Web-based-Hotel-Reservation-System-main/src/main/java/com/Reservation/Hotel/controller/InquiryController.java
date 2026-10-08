package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.model.AppUser;
import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.model.Inquiry;
import com.Reservation.Hotel.service.HotelService;
import com.Reservation.Hotel.service.InquiryService;
import com.Reservation.Hotel.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/inquiries")
public class InquiryController {

    private final InquiryService inquiryService;
    private final HotelService hotelService;
    private final UserService userService;

    public InquiryController(InquiryService inquiryService, HotelService hotelService, UserService userService) {
        this.inquiryService = inquiryService;
        this.hotelService = hotelService;
        this.userService = userService;
    }

    @GetMapping
    public String listInquiries(@RequestParam(value = "priority", required = false) String priority,
                                Authentication authentication,
                                Model model) {
        model.addAttribute("receivedInquiries", inquiryService.getReceivedInquiries(authentication, priority));
        model.addAttribute("submittedInquiries", inquiryService.getSubmittedInquiries(authentication, priority));
        model.addAttribute("selectedPriority", priority != null ? priority : "ALL");
        return "inquiries/index";
    }

    private void prepareForm(Model model) {
        model.addAttribute("hotels", hotelService.filterAndSearchHotels(Hotel.APPROVED, null));
    }

    @GetMapping("/new")
    public String showCreateForm(@RequestParam(value = "hotelId", required = false) Long hotelId,
                                 Authentication authentication, Model model) {
        Inquiry inquiry = new Inquiry();
        AppUser me = userService.findByUsername(authentication.getName()).orElse(null);
        if (me != null) {
            inquiry.setName(me.getFullName());
            inquiry.setEmail(me.getEmail());
        }
        model.addAttribute("inquiry", inquiry);
        model.addAttribute("selectedHotelId", hotelId);
        prepareForm(model);
        return "inquiries/create";
    }

    @PostMapping("/save")
    public String saveInquiry(@Valid @ModelAttribute("inquiry") Inquiry inquiry,
                              BindingResult bindingResult,
                              @RequestParam(value = "hotelId", required = false) Long hotelId,
                              Authentication authentication,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        Hotel hotel = hotelService.getHotelById(hotelId);
        if (hotel != null && !hotel.isApproved()) hotel = null;
        if (bindingResult.hasErrors()) {
            model.addAttribute("selectedHotelId", hotelId);
            prepareForm(model);
            return "inquiries/create";
        }
        Inquiry saved = inquiryService.submit(inquiry, hotel, authentication);
        redirectAttributes.addFlashAttribute("successMessage", "Inquiry submitted - your ticket number is "
                + saved.getTicketNumber() + ". You will be notified when " + saved.getRecipientLabel() + " replies.");
        return "redirect:/inquiries";
    }

    // ---------------- sender edits / withdraws their own pending inquiry ----------------

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Authentication authentication, Model model,
                               RedirectAttributes redirectAttributes) {
        Inquiry inquiry = inquiryService.getInquiryById(id);
        if (!inquiryService.canEditOwn(inquiry, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only your own unanswered inquiries can be edited.");
            return "redirect:/inquiries";
        }
        model.addAttribute("inquiry", inquiry);
        model.addAttribute("selectedHotelId", inquiry.getHotel() != null ? inquiry.getHotel().getId() : null);
        prepareForm(model);
        return "inquiries/create";
    }

    @PostMapping("/{id}/update")
    public String updateInquiry(@PathVariable Long id,
                                @Valid @ModelAttribute("inquiry") Inquiry form,
                                BindingResult bindingResult,
                                @RequestParam(value = "hotelId", required = false) Long hotelId,
                                Authentication authentication, Model model,
                                RedirectAttributes redirectAttributes) {
        Inquiry existing = inquiryService.getInquiryById(id);
        if (!inquiryService.canEditOwn(existing, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only your own unanswered inquiries can be edited.");
            return "redirect:/inquiries";
        }
        if (bindingResult.hasErrors()) {
            form.setId(id);
            model.addAttribute("selectedHotelId", hotelId);
            prepareForm(model);
            return "inquiries/create";
        }
        Hotel hotel = hotelService.getHotelById(hotelId);
        if (hotel != null && !hotel.isApproved()) hotel = null;
        inquiryService.updateOwn(existing, form, hotel);
        redirectAttributes.addFlashAttribute("successMessage", "Inquiry " + existing.getDisplayTicket() + " updated.");
        return "redirect:/inquiries";
    }

    @PostMapping("/{id}/withdraw")
    public String withdrawInquiry(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        Inquiry inquiry = inquiryService.getInquiryById(id);
        if (!inquiryService.canEditOwn(inquiry, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only your own unanswered inquiries can be withdrawn.");
            return "redirect:/inquiries";
        }
        inquiryService.deleteInquiry(id);
        redirectAttributes.addFlashAttribute("successMessage", "Inquiry withdrawn.");
        return "redirect:/inquiries";
    }

    @GetMapping("/{id}/respond")
    public String showRespondForm(@PathVariable Long id, Authentication authentication, Model model,
                                  RedirectAttributes redirectAttributes) {
        Inquiry inquiry = inquiryService.getInquiryById(id);
        if (!inquiryService.canHandle(inquiry, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You cannot answer this inquiry.");
            return "redirect:/inquiries";
        }
        model.addAttribute("inquiry", inquiry);
        return "inquiries/respond";
    }

    @PostMapping("/{id}/respond")
    public String respondInquiry(@PathVariable Long id,
                                 @RequestParam("response") String response,
                                 @RequestParam("action") String action,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        Inquiry inquiry = inquiryService.getInquiryById(id);
        if (!inquiryService.canHandle(inquiry, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You cannot answer this inquiry.");
            return "redirect:/inquiries";
        }
        if (response == null || response.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please write a response.");
            return "redirect:/inquiries/" + id + "/respond";
        }
        boolean ignore = "ignore".equalsIgnoreCase(action);
        inquiryService.respond(inquiry, response, ignore, authentication.getName());
        redirectAttributes.addFlashAttribute("successMessage", ignore
                ? "Inquiry closed with reason - the sender has been notified."
                : "Response sent - the sender has been notified.");
        return "redirect:/inquiries";
    }

    @PostMapping("/{id}/delete")
    public String deleteInquiry(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        Inquiry inquiry = inquiryService.getInquiryById(id);
        if (!inquiryService.canHandle(inquiry, authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You cannot delete this inquiry.");
            return "redirect:/inquiries";
        }
        inquiryService.deleteInquiry(id);
        redirectAttributes.addFlashAttribute("successMessage", "Inquiry deleted successfully!");
        return "redirect:/inquiries";
    }
}
