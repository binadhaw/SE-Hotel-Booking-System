package com.Reservation.Hotel.service;

import com.Reservation.Hotel.model.AppUser;
import com.Reservation.Hotel.model.Booking;
import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.repository.AgentProfileRepository;
import com.Reservation.Hotel.repository.BookingRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** Platform-wide numbers for the admin dashboard (PBI-14). */
@Service
public class DashboardService {

    /** One column of a monthly bar chart. */
    public static class MonthBar {
        private final String label;
        private final double value;
        private final double heightPct;
        private final String display;

        public MonthBar(String label, double value, double heightPct, String display) {
            this.label = label;
            this.value = value;
            this.heightPct = heightPct;
            this.display = display;
        }

        public String getLabel() { return label; }
        public double getValue() { return value; }
        public double getHeightPct() { return heightPct; }
        public String getDisplay() { return display; }
    }

    /** A hotel with its booking count and confirmed revenue. */
    public static class HotelRank {
        private final Hotel hotel;
        private final long bookings;
        private final double revenue;

        public HotelRank(Hotel hotel, long bookings, double revenue) {
            this.hotel = hotel;
            this.bookings = bookings;
            this.revenue = revenue;
        }

        public Hotel getHotel() { return hotel; }
        public long getBookings() { return bookings; }
        public double getRevenue() { return revenue; }
    }

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yy");

    private final BookingRepository bookingRepository;
    private final HotelService hotelService;
    private final UserService userService;
    private final InquiryService inquiryService;
    private final AgentProfileRepository agentRepository;

    public DashboardService(BookingRepository bookingRepository, HotelService hotelService, UserService userService,
                            InquiryService inquiryService, AgentProfileRepository agentRepository) {
        this.bookingRepository = bookingRepository;
        this.hotelService = hotelService;
        this.userService = userService;
        this.inquiryService = inquiryService;
        this.agentRepository = agentRepository;
    }

    /** Fills every number the dashboard page shows. */
    public Map<String, Object> adminStats() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Booking> bookings = bookingRepository.findAll();

        m.put("userCount", userService.countAll());
        m.put("tourists", userService.countByRole(AppUser.ROLE_USER));
        m.put("managers", userService.countByRole(AppUser.ROLE_MANAGER));
        m.put("agents", userService.countByRole(AppUser.ROLE_AGENT));

        m.put("hotelsApproved", hotelService.countByStatus(Hotel.APPROVED));
        m.put("hotelsPending", hotelService.countByStatus(Hotel.PENDING));
        m.put("hotelsRejected", hotelService.countByStatus(Hotel.REJECTED));
        m.put("hotelsRemoved", hotelService.countByStatus(Hotel.REMOVED));

        Map<String, Long> byStatus = bookings.stream()
                .collect(Collectors.groupingBy(b -> b.getStatus() == null ? "UNKNOWN" : b.getStatus(), TreeMap::new, Collectors.counting()));
        m.put("bookingCount", bookings.size());
        m.put("bookingsByStatus", byStatus);
        m.put("pendingBookings", byStatus.getOrDefault("PENDING", 0L));

        double revenue = bookings.stream().filter(b -> "APPROVED".equals(b.getStatus())).mapToDouble(Booking::getAmountPaid).sum();
        double refundsDue = bookings.stream().filter(b -> "REFUND_PENDING".equals(b.getStatus()))
                .mapToDouble(b -> Math.abs(b.getBalanceDue())).sum();
        m.put("revenue", revenue);
        m.put("refundsDue", refundsDue);
        m.put("refundsPending", byStatus.getOrDefault("REFUND_PENDING", 0L));

        m.put("agentsPending", (long) agentRepository.findByStatus("PENDING").size());
        m.put("inquiriesPending", inquiryService.countPending());

        m.put("bookingBars", monthlyBookings(bookings, 6));
        m.put("revenueBars", monthlyRevenue(bookings, 6));
        m.put("topHotels", topHotels(bookings, 5));
        return m;
    }

    private static YearMonth monthOf(Booking b) {
        if (b.getCreatedAt() != null) return YearMonth.from(b.getCreatedAt());
        return b.getCheckInDate() != null ? YearMonth.from(b.getCheckInDate()) : null;
    }

    private static List<YearMonth> lastMonths(int n) {
        YearMonth now = YearMonth.from(LocalDate.now());
        List<YearMonth> months = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) months.add(now.minusMonths(i));
        return months;
    }

    /** Booking requests made per month (by request date). */
    public List<MonthBar> monthlyBookings(List<Booking> bookings, int months) {
        Map<YearMonth, Double> counts = new HashMap<>();
        for (Booking b : bookings) {
            YearMonth ym = monthOf(b);
            if (ym != null) counts.merge(ym, 1.0, Double::sum);
        }
        return bars(lastMonths(months), counts, false);
    }

    /** Confirmed (approved) revenue per month of stay. */
    public List<MonthBar> monthlyRevenue(List<Booking> bookings, int months) {
        Map<YearMonth, Double> sums = new HashMap<>();
        for (Booking b : bookings) {
            if (!"APPROVED".equals(b.getStatus()) || b.getCheckInDate() == null) continue;
            sums.merge(YearMonth.from(b.getCheckInDate()), b.getAmountPaid(), Double::sum);
        }
        return bars(lastMonths(months), sums, true);
    }

    private List<MonthBar> bars(List<YearMonth> months, Map<YearMonth, Double> values, boolean money) {
        double max = months.stream().mapToDouble(m -> values.getOrDefault(m, 0.0)).max().orElse(0);
        List<MonthBar> bars = new ArrayList<>();
        for (YearMonth ym : months) {
            double v = values.getOrDefault(ym, 0.0);
            double pct = max > 0 ? v / max * 100.0 : 0;
            bars.add(new MonthBar(ym.format(MONTH), v, pct,
                    money ? String.format("$%,.0f", v) : String.format("%.0f", v)));
        }
        return bars;
    }

    /** Hotels with the most non-cancelled bookings. */
    public List<HotelRank> topHotels(List<Booking> bookings, int limit) {
        Map<Hotel, List<Booking>> byHotel = bookings.stream()
                .filter(b -> b.getHotel() != null && !"CANCELLED".equals(b.getStatus()))
                .collect(Collectors.groupingBy(Booking::getHotel));
        return byHotel.entrySet().stream()
                .map(e -> new HotelRank(e.getKey(), e.getValue().size(),
                        e.getValue().stream().filter(b -> "APPROVED".equals(b.getStatus())).mapToDouble(Booking::getAmountPaid).sum()))
                .sorted(Comparator.comparing(HotelRank::getBookings).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}
