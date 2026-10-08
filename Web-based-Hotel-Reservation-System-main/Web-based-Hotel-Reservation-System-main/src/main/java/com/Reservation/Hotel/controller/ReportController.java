package com.Reservation.Hotel.controller;

import com.Reservation.Hotel.model.Booking;
import com.Reservation.Hotel.service.BookingService;
import com.Reservation.Hotel.service.HotelService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Booking reports as CSV: administrators get every booking, a manager the bookings of their own hotels. */
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final BookingService bookingService;

    public ReportController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/bookings.csv")
    public ResponseEntity<byte[]> bookingsCsv(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        List<Booking> bookings = HotelService.hasRole(authentication, "ADMIN")
                ? bookingService.getAllBookings()
                : bookingService.getBookingsForManager(authentication.getName());
        bookings = bookings.stream()
                .filter(b -> from == null || (b.getCheckInDate() != null && !b.getCheckInDate().isBefore(from)))
                .filter(b -> to == null || (b.getCheckInDate() != null && !b.getCheckInDate().isAfter(to)))
                .sorted(Comparator.comparing(Booking::getCheckInDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        StringBuilder csv = new StringBuilder("﻿"); // BOM so Excel opens UTF-8 correctly
        csv.append("Reference,Group,Hotel,Location,Room,Guest,Guests,Check-in,Check-out,Nights,Status,Original,Discount,Total,Paid,Balance,Promo code,Created\n");
        for (Booking b : bookings) {
            long nights = b.getCheckInDate() != null && b.getCheckOutDate() != null
                    ? java.time.temporal.ChronoUnit.DAYS.between(b.getCheckInDate(), b.getCheckOutDate()) : 0;
            csv.append(String.join(",",
                    cell(b.getDisplayReference()), cell(b.getGroupReference()),
                    cell(b.getHotel() != null ? b.getHotel().getName() : ""), cell(b.getHotel() != null ? b.getHotel().getLocation() : ""),
                    cell(b.getRoom() != null ? b.getRoom().getRoomNumber() + " " + b.getRoom().getRoomType() : ""),
                    cell(b.getUsername()), cell(b.getGuests()),
                    cell(b.getCheckInDate()), cell(b.getCheckOutDate()), cell(nights), cell(b.getStatus()),
                    money(b.getOriginalAmount()), money(b.getDiscountAmount()), money(b.getFinalAmount()),
                    money(b.getAmountPaid()), money(b.getBalanceDue()), cell(b.getPromoCode()), cell(b.getCreatedAt())))
                    .append('\n');
        }
        String name = "bookings-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String money(double v) {
        return String.format("%.2f", v);
    }

    /** Quotes a CSV cell; values starting with = + - @ are prefixed so spreadsheets don't run them as formulas. */
    private static String cell(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
