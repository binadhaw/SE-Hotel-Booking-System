package com.Reservation.Hotel;

import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.payment.CardDetails;
import com.Reservation.Hotel.payment.PaymentService;
import com.Reservation.Hotel.repository.HotelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Several guests confirming the same room for the same nights at the same moment must never all win:
 * exactly one booking may end up APPROVED (regression test for the double-booking race).
 */
@SpringBootTest
class ConcurrentBookingTest {

    private static final int GUESTS = 8;

    @Autowired HotelRepository hotelRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired BookingService bookingService;
    @Autowired PaymentService paymentService;

    /** One room plus a PENDING request from each guest, all for the same nights. */
    private List<Long> pendingRequestsForOneRoom(String name, LocalDate in, LocalDate out, String paymentMethod) {
        Hotel hotel = new Hotel(name, "Galle", Hotel.APPROVED, "test", "Free WiFi");
        hotel.setManagerUsername("manager");
        hotelRepository.save(hotel);
        Room room = roomRepository.save(new Room("1", "Deluxe", 100.0, true, hotel));

        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < GUESTS; i++) {
            Booking b = new Booking();
            b.setHotel(hotel);
            b.setRoom(room);
            b.setUsername("guest" + i);
            b.setCheckInDate(in);
            b.setCheckOutDate(out);
            b.setGuests(1);
            b.setOriginalAmount(200.0);
            b.setFinalAmount(200.0);
            b.setBalanceDue(200.0);
            b.setPaymentMethod(paymentMethod);
            b.setReference("BK-RACE" + name.length() + i);
            b.setStatus("PENDING");
            ids.add(bookingRepository.save(b).getId());
        }
        return ids;
    }

    /** Runs one task per booking, all released at the same instant. */
    private void runTogether(List<Long> ids, java.util.function.Function<Long, Callable<Object>> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(ids.size());
        CyclicBarrier start = new CyclicBarrier(ids.size());
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (Long id : ids) {
                Callable<Object> work = task.apply(id);
                results.add(pool.submit(() -> {
                    start.await();
                    return work.call();
                }));
            }
            for (Future<Object> f : results) f.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private long approvedCount(List<Long> ids) {
        return bookingRepository.findAllById(ids).stream().filter(b -> "APPROVED".equals(b.getStatus())).count();
    }

    @Test
    void simultaneousCardPaymentsForTheSameRoomConfirmOnlyOne() throws Exception {
        LocalDate in = LocalDate.now().plusDays(40);
        List<Long> ids = pendingRequestsForOneRoom("Race Card Inn", in, in.plusDays(2), "CARD");

        runTogether(ids, id -> () -> {
            Booking b = bookingRepository.findById(id).orElseThrow();
            return paymentService.pay(List.of(b), new CardDetails("Race Tester", "4242 4242 4242 4242", "12/40", "123"),
                    b.getUsername());
        });

        assertEquals(1, approvedCount(ids), "exactly one guest gets the room");
    }

    @Test
    void simultaneousManagerApprovalsForTheSameRoomConfirmOnlyOne() throws Exception {
        LocalDate in = LocalDate.now().plusDays(50);
        List<Long> ids = pendingRequestsForOneRoom("Race Approval Lodge", in, in.plusDays(3), "BANK_TRANSFER");

        runTogether(ids, id -> () -> bookingService.approve(bookingRepository.findById(id).orElseThrow()));

        assertEquals(1, approvedCount(ids), "exactly one request is approved");
    }

    @Test
    void simultaneousEditsOntoTheSameNightsKeepOnlyOne() throws Exception {
        LocalDate target = LocalDate.now().plusDays(80);
        List<Long> ids = pendingRequestsForOneRoom("Race Edit House", target, target.plusDays(1), "BANK_TRANSFER");
        // Each guest already holds the room on their own, separate nights
        for (int i = 0; i < ids.size(); i++) {
            Booking b = bookingRepository.findById(ids.get(i)).orElseThrow();
            b.setCheckInDate(target.plusDays(10L + 2L * i));
            b.setCheckOutDate(target.plusDays(11L + 2L * i));
            b.setStatus("APPROVED");
            bookingRepository.save(b);
        }

        // ...and all of them move to the same night at once (same price, so each edit would stay APPROVED)
        runTogether(ids, id -> () -> {
            Booking b = bookingRepository.findById(id).orElseThrow();
            b.setCheckInDate(target);
            b.setCheckOutDate(target.plusDays(1));
            return bookingService.saveEdit(b);
        });

        long onTarget = bookingRepository.findAllById(ids).stream()
                .filter(b -> target.equals(b.getCheckInDate())).count();
        assertEquals(1, onTarget, "only one guest may move onto the night");
        assertEquals(ids.size(), approvedCount(ids), "refused edits leave the original booking untouched");
    }
}
