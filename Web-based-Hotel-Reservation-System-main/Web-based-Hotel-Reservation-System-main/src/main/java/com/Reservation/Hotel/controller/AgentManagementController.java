package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.model.AgentProfile;
import com.Reservation.Hotel.service.AgentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class AgentManagementController {

    private final AgentService agentService;

    public AgentManagementController(AgentService agentService) {
        this.agentService = agentService;
    }

    /** The logged-in agent's own performance (previously these counts were platform-wide). */
    @GetMapping("/agent/dashboard")
    public String agentDashboard(Model model, Principal principal) {
        long[] c = agentService.statusCounts(principal.getName());
        long pendingCount = c[0] + c[1] + c[2];   // waiting for a plan, waiting for the tourist, revision asked
        long acceptedCount = c[3];
        long rejectedCount = c[4];
        long totalRequests = pendingCount + acceptedCount + rejectedCount;

        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("acceptedCount", acceptedCount);
        model.addAttribute("rejectedCount", rejectedCount);
        model.addAttribute("totalRequests", totalRequests);
        model.addAttribute("acceptanceRate", rate(acceptedCount, totalRequests));
        model.addAttribute("rejectionRate", rate(rejectedCount, totalRequests));
        model.addAttribute("pendingRate", rate(pendingCount, totalRequests));
        AgentProfile profile = agentService.getProfileByUsername(principal.getName());
        model.addAttribute("profile", profile);
        return "agent-dashboard";
    }

    private static double rate(long part, long total) {
        return total > 0 ? Math.round((double) part / total * 100.0) : 0.0;
    }

    @GetMapping("/agent/workspace")
    public String agentWorkspace() {
        return "redirect:/agents/portal";
    }
}
