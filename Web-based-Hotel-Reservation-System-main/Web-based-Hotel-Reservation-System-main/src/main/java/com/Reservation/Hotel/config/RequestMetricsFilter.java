package com.Reservation.Hotel.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Times every page request (static files excluded) for the admin "System health" panel.
 * Runs first, so the time includes security checks and a crashed request is counted as a server error.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestMetricsFilter extends OncePerRequestFilter {

    private final SystemHealthService systemHealth;

    public RequestMetricsFilter(SystemHealthService systemHealth) {
        this.systemHealth = systemHealth;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/uploads/") || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        int status = 500;
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            systemHealth.record(request.getMethod(), request.getRequestURI(), status, (System.nanoTime() - start) / 1_000_000);
        }
    }
}
