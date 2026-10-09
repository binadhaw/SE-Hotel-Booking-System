package com.Reservation.Hotel.config;

import com.Reservation.Hotel.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserService userService) throws Exception {
        // After login: go back to the page the visitor was trying to open, otherwise to /dashboard
        // (which forwards to the right home page for the user's role).
        SavedRequestAwareAuthenticationSuccessHandler successHandler = new SavedRequestAwareAuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(jakarta.servlet.http.HttpServletRequest request,
                                                jakarta.servlet.http.HttpServletResponse response,
                                                org.springframework.security.core.Authentication authentication)
                    throws jakarta.servlet.ServletException, java.io.IOException {
                userService.recordLogin(authentication.getName());
                super.onAuthenticationSuccess(request, response, authentication);
            }
        };
        successHandler.setDefaultTargetUrl("/dashboard");

        http
                .authorizeHttpRequests(auth -> auth
                        // ---------- Public (no login needed: PBI-09 / PBI-11) ----------
                        .requestMatchers("/", "/login", "/register", "/forgot-password", "/forgot-password/**",
                                "/privacy", "/error", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/uploads/hotels/**", "/uploads/rooms/**").permitAll()
                        // Payment receipts are only served by BookingController.receiptFile (owner/manager/admin check)
                        .requestMatchers("/uploads/receipts/**").denyAll()

                        // ---------- Admin ----------
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/reports/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/hotels/*/approve", "/hotels/*/reject").hasRole("ADMIN")

                        // ---------- Hotel Management ----------
                        .requestMatchers("/hotels/new", "/hotels/*/edit", "/hotels/*/update", "/hotels/*/delete").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/hotels").hasAnyRole("MANAGER", "ADMIN")

                        // ---------- Staff Management (owning manager; admins can view) ----------
                        .requestMatchers("/hotels/*/staff", "/staff/**").hasAnyRole("MANAGER", "ADMIN")

                        // ---------- Room Management (Admin can delete only; Manager can add, edit, update, delete) ----------
                        .requestMatchers("/hotels/*/rooms/new", "/rooms/*/edit", "/rooms/*/update").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.POST, "/hotels/*/rooms").hasRole("MANAGER")
                        .requestMatchers("/rooms/*/delete").hasAnyRole("MANAGER", "ADMIN")

                        // ---------- Bookings (Manager handles approvals etc.; Admin is view-only) ----------
                        .requestMatchers("/bookings/*/approve", "/bookings/*/reject", "/bookings/*/release-room", "/bookings/*/manager-cancel", "/bookings/*/settle-refund", "/bookings/*/delete").hasRole("MANAGER")
                        .requestMatchers("/bookings/new/**", "/payments/**", "/gateway/**").hasAnyRole("USER", "AGENT")

                        // ---------- Promotion ----------
                        .requestMatchers("/promotions/new/**", "/promotions/create-type", "/promotions/*/edit", "/promotions/*/update", "/promotions/*/delete", "/promotions/*/send").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/promotions").hasAnyRole("MANAGER", "ADMIN")

                        // ---------- Inquiry & Feedback ----------
                        .requestMatchers("/inquiries/*/respond", "/inquiries/*/delete").hasAnyRole("MANAGER", "ADMIN")

                        // ---------- Itinerary & Local Experience ----------
                        .requestMatchers(HttpMethod.GET, "/agents/{id}/profile").permitAll()
                        .requestMatchers("/agents/portal", "/agents/workspace", "/agents/save-profile", "/agents/add-itinerary",
                                "/agents/itinerary/**", "/agents/proposal/**").hasRole("AGENT")
                        .requestMatchers("/agents/explore", "/agents/request/**").hasRole("USER")
                        .requestMatchers("/attractions/**").hasAnyRole("AGENT", "MANAGER", "ADMIN")
                        .requestMatchers("/reviews/new", "/reviews").hasRole("USER")
                        .requestMatchers("/agents/admin/**").hasRole("ADMIN")
                        .requestMatchers("/agent/**").hasRole("AGENT")

                        // Public hotel browsing - listed last so "/hotels/new" etc. above take priority over "/hotels/{id}"
                        .requestMatchers(HttpMethod.GET, "/hotels", "/hotels/search", "/hotels/{id}", "/hotels/{id}/rooms").permitAll()

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(successHandler)
                        .failureHandler((request, response, exception) -> {
                            String reason = exception instanceof org.springframework.security.authentication.DisabledException
                                    ? "suspended" : "bad";
                            response.sendRedirect(request.getContextPath() + "/login?error=" + reason);
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                // Security headers. Spring already sends X-Frame-Options: DENY, X-Content-Type-Options: nosniff and,
                // on HTTPS, Strict-Transport-Security; also limit what the Referer header leaks to other sites.
                .headers(headers -> headers.referrerPolicy(ref -> ref.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));

        return http.build();
    }
}
