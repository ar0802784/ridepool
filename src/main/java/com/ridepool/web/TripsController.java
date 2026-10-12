package com.ridepool.web;

import com.ridepool.model.Booking;
import com.ridepool.model.RideRequest;
import com.ridepool.repo.BookingRepository;
import com.ridepool.repo.RideRequestRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** "My trips": every ride the logged-in user has requested, newest first, with the service they booked (if any). */
@Controller
public class TripsController {

    /** One row on the My trips page. */
    public record Trip(RideRequest ride, Booking booking) {}

    private final RideRequestRepository rides;
    private final BookingRepository bookings;

    public TripsController(RideRequestRepository rides, BookingRepository bookings) {
        this.rides = rides;
        this.bookings = bookings;
    }

    @GetMapping("/trips")
    public String trips(HttpSession session, Model model) {
        Long uid = (Long) session.getAttribute(AuthInterceptor.SESSION_USER_ID);

        // Bookings come newest first, so putIfAbsent keeps the latest booking for each ride.
        Map<Long, Booking> latestBooking = new HashMap<>();
        for (Booking b : bookings.findByUserIdOrderByCreatedAtDesc(uid)) {
            latestBooking.putIfAbsent(b.getRide().getId(), b);
        }

        List<Trip> trips = rides.findByUserIdOrderByCreatedAtDesc(uid).stream()
                .map(r -> new Trip(r, latestBooking.get(r.getId())))
                .toList();

        model.addAttribute("trips", trips);
        return "trips";
    }
}
