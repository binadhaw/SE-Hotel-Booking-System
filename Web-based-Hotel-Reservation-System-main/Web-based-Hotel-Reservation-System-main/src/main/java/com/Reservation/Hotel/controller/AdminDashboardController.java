package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.service.ActivityLogService;
import com.Reservation.Hotel.service.DashboardService;
import com.Reservation.Hotel.service.SystemHealthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;

/** PBI-14: administrators monitor platform-wide activity. */
@Controller
@RequestMapping("/admin")
public class AdminDashboardController {
 //
    private static final List<String> LOG_FILTERS = List.of("HOTEL", "USER", "BOOKING", "AGENT", "REVIEW", "PROMOTION");

    private final DashboardService dashboardService;
    private final ActivityLogService activityLog;
    private final SystemHealthService systemHealth;

    public AdminDashboardController(DashboardService dashboardService, ActivityLogService activityLog,
                                    SystemHealthService systemHealth) {
        this.dashboardService = dashboardService;
        this.activityLog = activityLog;
        this.systemHealth = systemHealth;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAllAttributes(dashboardService.adminStats());
        model.addAttribute("recentActivity", activityLog.recent());
        model.addAttribute("health", systemHealth.snapshot());
        return "admin/dashboard";
    }

    @GetMapping("/activity")
    public String activity(@RequestParam(value = "type", required = false) String type,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           Model model) {
        String prefix = type != null && LOG_FILTERS.contains(type.toUpperCase(Locale.ROOT)) ? type.toUpperCase(Locale.ROOT) : "";
        model.addAttribute("logPage", activityLog.page(prefix, page));
        model.addAttribute("type", prefix);
        model.addAttribute("filters", LOG_FILTERS);
        return "admin/activity";
    }
}
