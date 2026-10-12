package com.ridepool.web;

import com.ridepool.model.Booking;
import com.ridepool.model.RideRequest;
import com.ridepool.repo.BookingRepository;
import com.ridepool.repo.RideRequestRepository;
import com.ridepool.service.FareQuote;
import com.ridepool.service.FareService;
import com.ridepool.service.GeoService;
import com.ridepool.service.MatchResult;
import com.ridepool.service.MatchService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class BookingController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a");

    /** Plain websites, used when a service has no public deep link that can carry the trip. */
    private static final Map<String, String> APP_LINKS = Map.of(
            "Ola", "https://www.olacabs.com",
            "Uber", "https://m.uber.com",
            "Rapido", "https://www.rapido.bike",
            "inDrive", "https://indrive.com");

    /** The link to open for a booking, and whether it carries the pickup and drop with it. */
    public record AppLink(String url, boolean prefilled) {}

    private static String enc(Object v) {
        return URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8);
    }

    /**
     * Builds the link that opens the chosen service with pickup and drop already filled in.
     * Uber and Ola publish deep links that accept coordinates. Rapido and inDrive publish none,
     * so they open the plain site and the page offers copy buttons instead.
     */
    static AppLink buildAppLink(String provider, RideRequest ride) {
        String fromShort = shorten(ride.getFromName());
        String toShort = shorten(ride.getToName());

        switch (provider) {
            case "Uber": {
                String url = "https://m.uber.com/ul/?action=setPickup"
                        + "&pickup%5Blatitude%5D=" + enc(ride.getFromLat())
                        + "&pickup%5Blongitude%5D=" + enc(ride.getFromLng())
                        + "&pickup%5Bnickname%5D=" + enc(fromShort)
                        + "&pickup%5Bformatted_address%5D=" + enc(ride.getFromName())
                        + "&dropoff%5Blatitude%5D=" + enc(ride.getToLat())
                        + "&dropoff%5Blongitude%5D=" + enc(ride.getToLng())
                        + "&dropoff%5Bnickname%5D=" + enc(toShort)
                        + "&dropoff%5Bformatted_address%5D=" + enc(ride.getToName());
                return new AppLink(url, true);
            }
            case "Ola": {
                String url = "https://olawebcdn.com/assets/ola-universal-link.html"
                        + "?lat=" + enc(ride.getFromLat())
                        + "&lng=" + enc(ride.getFromLng())
                        + "&pickup_name=" + enc(fromShort)
                        + "&drop_lat=" + enc(ride.getToLat())
                        + "&drop_lng=" + enc(ride.getToLng())
                        + "&drop_name=" + enc(toShort)
                        + "&category=mini&landing_page=bk&utm_source=ridepool";
                return new AppLink(url, true);
            }
            default:
                return new AppLink(APP_LINKS.getOrDefault(provider, "https://www.google.com"), false);
        }
    }

    private final RideRequestRepository rides;
    private final BookingRepository bookings;
    private final MatchService matcher;
    private final FareService fares;
    private final GeoService geo;

    public BookingController(RideRequestRepository rides, BookingRepository bookings,
                             MatchService matcher, FareService fares, GeoService geo) {
        this.rides = rides;
        this.bookings = bookings;
        this.matcher = matcher;
        this.fares = fares;
        this.geo = geo;
    }

    /** "Book now": saves the chosen service and shows the booking summary. */
    @PostMapping("/book")
    public String book(@RequestParam Long rideId, @RequestParam String provider, HttpSession session) {
        RideRequest ride = ownRide(rideId, session).orElse(null);
        if (ride == null) {
            return "redirect:/request";
        }

        // Work the fare out again on the server so the price cannot be tampered with in the browser.
        List<MatchResult> matches = matcher.findMatches(ride);
        int groupSize = Math.min(matches.size() + 1, 4);
        double km = geo.roadDistanceKm(ride.getFromLat(), ride.getFromLng(), ride.getToLat(), ride.getToLng());
        List<FareQuote> quotes = fares.quotes(km, geo.durationMinutes(km), ride.getDepartureTime(), groupSize);

        FareQuote chosen = quotes.stream()
                .filter(q -> q.getProvider().equalsIgnoreCase(provider))
                .findFirst().orElse(null);
        if (chosen == null) {
            return "redirect:/results/" + rideId;
        }

        Booking b = new Booking();
        b.setUser(ride.getUser());
        b.setRide(ride);
        b.setProvider(chosen.getProvider());
        b.setVehicle(chosen.getVehicle());
        b.setTotalFare(chosen.getTotalFare());
        b.setPerPerson(chosen.getPerPerson());
        b.setGroupSize(chosen.isShareable() ? groupSize : 1);
        b = bookings.save(b);
        return "redirect:/booking/" + b.getId();
    }

    @GetMapping("/booking/{id}")
    public String summary(@PathVariable Long id, HttpSession session, Model model) {
        Long uid = (Long) session.getAttribute(AuthInterceptor.SESSION_USER_ID);
        Booking b = bookings.findById(id).filter(x -> x.getUser().getId().equals(uid)).orElse(null);
        if (b == null) {
            return "redirect:/request";
        }
        RideRequest ride = b.getRide();
        List<MatchResult> matches = b.getGroupSize() > 1 ? matcher.findMatches(ride) : List.of();

        String draft = "Hi! I'm going with " + b.getProvider() + " (" + b.getVehicle() + ") from "
                + shorten(ride.getFromName()) + " to " + shorten(ride.getToName())
                + " at " + ride.getDepartureTime().format(TIME) + ". Your share is about Rs "
                + b.getPerPerson() + ". Shall we meet at the pickup point?";

        model.addAttribute("booking", b);
        model.addAttribute("ride", ride);
        model.addAttribute("matches", matches);
        AppLink link = buildAppLink(b.getProvider(), ride);
        model.addAttribute("appLink", link.url());
        model.addAttribute("prefilled", link.prefilled());
        model.addAttribute("draft", draft);
        return "booking";
    }

    private static String shorten(String place) {
        int comma = place.indexOf(',');
        return comma > 0 ? place.substring(0, comma) : place;
    }

    private Optional<RideRequest> ownRide(Long id, HttpSession session) {
        Long uid = (Long) session.getAttribute(AuthInterceptor.SESSION_USER_ID);
        return rides.findById(id).filter(r -> r.getUser().getId().equals(uid));
    }
}
