package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.model.AgentProfile;
import com.Reservation.Hotel.model.AppUser;
import com.Reservation.Hotel.model.Attraction;
import com.Reservation.Hotel.model.Booking;
import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.model.VacationRequest;
import com.Reservation.Hotel.service.AgentService;
import com.Reservation.Hotel.service.AttractionService;
import com.Reservation.Hotel.service.BookingService;
import com.Reservation.Hotel.service.HotelService;
import com.Reservation.Hotel.service.UserService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import com.Reservation.Hotel.service.ActivityLogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/agents")
public class AgentController {

    private final AgentService agentService;
    private final AttractionService attractionService;
    private final HotelService hotelService;
    private final BookingService bookingService;
    private final UserService userService;

    private final ActivityLogService activityLog;

    public AgentController(AgentService agentService, AttractionService attractionService, HotelService hotelService,
                           BookingService bookingService, UserService userService, ActivityLogService activityLog) {
        this.activityLog = activityLog;
        this.agentService = agentService;
        this.attractionService = attractionService;
        this.hotelService = hotelService;
        this.bookingService = bookingService;
        this.userService = userService;
    }

    private void flash(RedirectAttributes ra, String problem, String success) {
        if (problem != null) ra.addFlashAttribute("errorMessage", problem);
        else ra.addFlashAttribute("successMessage", success);
    }

    // --- AGENT WORKSPACE & PORTAL ---

    @GetMapping({"/portal", "/workspace"})
    public String showAgentPortal(Principal principal, Model model) {
        String username = principal.getName();
        AgentProfile agentProfile = agentService.getProfileByUsername(username);
        if (agentProfile == null) {
            agentProfile = new AgentProfile();
            agentProfile.setUsername(username);
            AgentProfile draft = agentProfile;
            userService.findByUsername(username).ifPresent(u -> {
                draft.setAgencyName(u.getFullName());
                draft.setPhone(u.getPhone());
            });
        }
        if (!model.containsAttribute("agentProfile")) model.addAttribute("agentProfile", agentProfile);
        model.addAttribute("profile", agentProfile);
        model.addAttribute("incomingRequests", agentService.getRequestsForAgent(username));
        model.addAttribute("allAttractions", attractionService.all());
        model.addAttribute("myAttractions", attractionService.byCreator(username));
        model.addAttribute("hotels", hotelService.filterAndSearchHotels(Hotel.APPROVED, null));
        model.addAttribute("categories", Attraction.CATEGORIES);
        return "agents/portal";
    }

    @PostMapping("/save-profile")
    public String saveProfile(@Valid @ModelAttribute("agentProfile") AgentProfile agentProfile,
                              BindingResult bindingResult,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/agents/portal";
        }
        AgentProfile saved = agentService.saveOwnProfile(principal.getName(), agentProfile);
        redirectAttributes.addFlashAttribute("successMessage", "APPROVED".equals(saved.getStatus())
                ? "Profile updated." : "Profile submitted for administrator review!");
        return "redirect:/agents/portal";
    }

    @PostMapping("/add-itinerary")
    public String addItinerary(@RequestParam("title") String title,
                               @RequestParam(value = "description", required = false) String description,
                               @RequestParam("durationDays") int durationDays,
                               @RequestParam("estimatedCost") double estimatedCost,
                               Principal principal, RedirectAttributes redirectAttributes) {
        flash(redirectAttributes, agentService.addSampleItinerary(principal.getName(), title, description, durationDays, estimatedCost),
                "Sample itinerary added to your public profile.");
        return "redirect:/agents/portal#samples";
    }

    @PostMapping("/itinerary/{id}/update")
    public String updateItinerary(@PathVariable Long id,
                                  @RequestParam("title") String title,
                                  @RequestParam(value = "description", required = false) String description,
                                  @RequestParam("durationDays") int durationDays,
                                  @RequestParam("estimatedCost") double estimatedCost,
                                  Principal principal, RedirectAttributes redirectAttributes) {
        flash(redirectAttributes, agentService.updateSampleItinerary(principal.getName(), id, title, description, durationDays, estimatedCost),
                "Sample itinerary updated.");
        return "redirect:/agents/portal#samples";
    }

    @PostMapping("/itinerary/{id}/delete")
    public String deleteItinerary(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        agentService.removeSampleItinerary(principal.getName(), id);
        redirectAttributes.addFlashAttribute("successMessage", "Sample itinerary removed.");
        return "redirect:/agents/portal#samples";
    }

    @PostMapping("/proposal/submit")
    public String submitProposal(@RequestParam("requestId") Long requestId,
                                 @RequestParam("proposedItineraryPlan") String proposedItineraryPlan,
                                 @RequestParam("proposedPrice") double proposedPrice,
                                 @RequestParam(value = "changeNote", required = false) String changeNote,
                                 @RequestParam(value = "attractionIds", required = false) List<Long> attractionIds,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        String problem = agentService.submitAgentProposal(requestId, principal.getName(), proposedItineraryPlan,
                proposedPrice, changeNote, attractionService.findAllById(attractionIds));
        flash(redirectAttributes, problem, "Itinerary sent - the tourist has been notified.");
        return "redirect:/agents/portal#requests";
    }

    // --- PUBLIC AGENT PROFILE (PBI-24) ---

    @GetMapping("/{id:\\d+}/profile")
    public String publicProfile(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        AgentProfile agent = agentService.getProfileById(id);
        if (agent == null || !"APPROVED".equals(agent.getStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "That travel agent profile is not available.");
            return "redirect:/";
        }
        model.addAttribute("agent", agent);
        model.addAttribute("experiences", attractionService.byCreator(agent.getUsername()));
        long[] counts = agentService.statusCounts(agent.getUsername());
        model.addAttribute("acceptedCount", counts[3]);
        model.addAttribute("hotels", hotelService.filterAndSearchHotels(Hotel.APPROVED, null));
        return "agents/profile";
    }

    // --- CUSTOMER AGENT EXPLORER & VACATION REQUESTS ---

    @GetMapping("/explore")
    public String exploreAgents(@RequestParam(value = "q", required = false) String q,
                                @RequestParam(value = "agentId", required = false) Long agentId,
                                Model model, Principal principal) {
        model.addAttribute("topAgents", agentService.getTop5ApprovedAgents());
        model.addAttribute("agents", agentService.searchApprovedAgents(q));
        model.addAttribute("q", q);
        model.addAttribute("openAgentId", agentId);
        model.addAttribute("myRequests", agentService.getRequestsForUser(principal.getName()));
        // Upcoming bookings the tourist can build the trip around
        List<Booking> upcoming = bookingService.getBookingsByUsername(principal.getName()).stream()
                .filter(b -> ("APPROVED".equals(b.getStatus()) || "PENDING".equals(b.getStatus()))
                        && b.getCheckOutDate() != null && !b.getCheckOutDate().isBefore(LocalDate.now()))
                .collect(Collectors.toList());
        model.addAttribute("myBookings", upcoming);
        AppUser me = userService.findByUsername(principal.getName()).orElse(null);
        model.addAttribute("me", me);
        return "agents/explore";
    }

    @PostMapping("/request/create")
    public String createVacationRequest(@RequestParam("agentId") Long agentId,
                                        @RequestParam("userName") String userName,
                                        @RequestParam("userEmail") String userEmail,
                                        @RequestParam("userPreferences") String userPreferences,
                                        @RequestParam(value = "travelStart", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate travelStart,
                                        @RequestParam(value = "travelEnd", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate travelEnd,
                                        @RequestParam(value = "travellers", required = false) Integer travellers,
                                        @RequestParam(value = "budget", required = false) Double budget,
                                        @RequestParam(value = "bookingReference", required = false) String bookingReference,
                                        Principal principal,
                                        RedirectAttributes redirectAttributes) {
        String problem = null;
        if (userName.isBlank() || userName.length() > 100) problem = "Please enter your name (up to 100 characters).";
        else if (!userEmail.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) problem = "Please enter a valid email address.";
        else if (userPreferences.isBlank() || userPreferences.length() > 1000) problem = "Describe your trip preferences (up to 1000 characters).";
        else if (travelStart != null && travelStart.isBefore(LocalDate.now())) problem = "The trip cannot start in the past.";
        else if (travellers != null && (travellers < 1 || travellers > 50)) problem = "Number of travellers must be 1-50.";
        else if (budget != null && budget < 0) problem = "Budget cannot be negative.";
        if (problem != null) {
            redirectAttributes.addFlashAttribute("errorMessage", problem);
            return "redirect:/agents/explore?agentId=" + agentId;
        }
        VacationRequest req = new VacationRequest();
        req.setUserName(userName.trim());
        req.setUserEmail(userEmail.trim());
        req.setUserPreferences(userPreferences.trim());
        req.setTravelStart(travelStart);
        req.setTravelEnd(travelEnd);
        req.setTravellers(travellers != null && travellers > 0 ? travellers : null);
        req.setBudget(budget != null && budget >= 0 ? budget : null);
        // Only link a booking that really belongs to this tourist
        if (bookingReference != null && !bookingReference.isBlank()) {
            boolean mine = bookingService.getBookingsByUsername(principal.getName()).stream()
                    .anyMatch(b -> bookingReference.equals(b.getReference()));
            if (mine) req.setBookingReference(bookingReference);
        }
        flash(redirectAttributes, agentService.createVacationRequest(agentId, req, principal.getName()),
                "Trip request sent! The agent will reply with a personalised itinerary.");
        return "redirect:/agents/explore#my-requests";
    }

    @PostMapping("/request/{id}/withdraw")
    public String withdrawRequest(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        flash(redirectAttributes, agentService.withdrawRequest(id, principal.getName()), "Trip request withdrawn.");
        return "redirect:/agents/explore#my-requests";
    }

    @PostMapping("/request/decision")
    public String handleProposalDecision(@RequestParam("requestId") Long requestId,
                                         @RequestParam("decision") String decision,
                                         @RequestParam(value = "feedback", required = false) String feedback,
                                         @RequestParam(value = "version", required = false) Integer version,
                                         Principal principal,
                                         RedirectAttributes redirectAttributes) {
        flash(redirectAttributes, agentService.handleUserDecision(requestId, principal.getName(), decision, feedback, version),
                "Decision sent to your travel agent.");
        return "redirect:/agents/explore#my-requests";
    }

    // --- ADMIN AGENT VERIFICATION & APPROVALS ---

    @GetMapping("/admin/manage")
    public String adminManageAgents(Model model) {
        model.addAttribute("pendingAgents", agentService.getPendingAgents());
        model.addAttribute("approvedAgents", agentService.getAllApprovedAgents());
        return "agents/admin_manage";
    }

    @PostMapping("/admin/{id}/approve")
    public String approveAgent(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        activityLog.log(principal.getName(), "AGENT_APPROVED", "Agent profile " + id);
        agentService.updateAgentAdminStatus(id, "APPROVED", null);
        redirectAttributes.addFlashAttribute("successMessage", "Agent approved successfully!");
        return "redirect:/agents/admin/manage";
    }

    @PostMapping("/admin/{id}/request-info")
    public String requestInfo(@PathVariable Long id,
                              @RequestParam("adminNote") String adminNote,
                              Principal principal, RedirectAttributes redirectAttributes) {
        activityLog.log(principal.getName(), "AGENT_INFO_REQUESTED", "Agent profile " + id + ": " + adminNote);
        agentService.updateAgentAdminStatus(id, "NEEDS_INFO", adminNote);
        redirectAttributes.addFlashAttribute("successMessage", "Information requested from agent.");
        return "redirect:/agents/admin/manage";
    }

    @PostMapping("/admin/{id}/delete")
    public String deleteAgent(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        activityLog.log(principal.getName(), "AGENT_REMOVED", "Agent profile " + id);
        agentService.deleteAgent(id);
        redirectAttributes.addFlashAttribute("successMessage", "Agent removed successfully.");
        return "redirect:/agents/admin/manage";
    }
}
