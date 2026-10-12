package com.ridepool.web;

import com.ridepool.model.RideRequest;
import com.ridepool.model.RideStatus;
import com.ridepool.model.User;
import com.ridepool.repo.RideRequestRepository;
import com.ridepool.repo.UserRepository;
import com.ridepool.service.DemoService;
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
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class RideController {

    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final UserRepository users;
    private final RideRequestRepository rides;
    private final GeoService geo;
    private final FareService fares;
    private final MatchService matcher;
    private final DemoService demo;

    public RideController(UserRepository users, RideRequestRepository rides, GeoService geo,
                          FareService fares, MatchService matcher, DemoService demo) {
        this.users = users;
        this.rides = rides;
        this.geo = geo;
        this.fares = fares;
        this.matcher = matcher;
        this.demo = demo;
    }

    // ---------- Step 1: where are you going? ----------

    @GetMapping("/request")
    public String requestPage(HttpSession session, Model model) {
        model.addAttribute("defaultTime",
                LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(0).format(INPUT_FORMAT));
        return "request";
    }

    @PostMapping("/request")
    public String createRequest(@RequestParam String fromName,
                                @RequestParam(required = false) Double fromLat,
                                @RequestParam(required = false) Double fromLng,
                                @RequestParam String toName,
                                @RequestParam(required = false) Double toLat,
                                @RequestParam(required = false) Double toLng,
                                @RequestParam String departure,
                                HttpSession session,
                                Model model) {
        String error = null;
        LocalDateTime when = null;

        if (fromLat == null || fromLng == null || toLat == null || toLng == null) {
            error = "Pick your pickup and drop places from the suggestion lists, or tap the map.";
        } else {
            try {
                when = LocalDateTime.parse(departure, INPUT_FORMAT);
            } catch (DateTimeParseException e) {
                error = "Choose a valid departure date and time.";
            }
        }
        if (error == null && GeoService.haversineKm(fromLat, fromLng, toLat, toLng) < 0.3) {
            error = "Pickup and drop are almost the same place. Choose a different drop.";
        }

        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("defaultTime", departure);
            model.addAttribute("fromName", fromName);
            model.addAttribute("toName", toName);
            return "request";
        }

        User user = users.findById(currentUserId(session)).orElseThrow();
        RideRequest ride = new RideRequest();
        ride.setUser(user);
        ride.setFromName(fromName.trim());
        ride.setFromLat(fromLat);
        ride.setFromLng(fromLng);
        ride.setToName(toName.trim());
        ride.setToLat(toLat);
        ride.setToLng(toLng);
        ride.setDepartureTime(when);
        ride = rides.save(ride);
        return "redirect:/searching/" + ride.getId();
    }

    // ---------- Step 2: looking for riders ----------

    @GetMapping("/searching/{id}")
    public String searching(@PathVariable Long id, HttpSession session, Model model) {
        RideRequest ride = ownRide(id, session).orElse(null);
        if (ride == null) {
            return "redirect:/request";
        }
        double km = geo.roadDistanceKm(ride.getFromLat(), ride.getFromLng(), ride.getToLat(), ride.getToLng());
        double minutes = geo.durationMinutes(km);
        List<FareQuote> solo = fares.quotes(km, minutes, ride.getDepartureTime(), 1);

        model.addAttribute("ride", ride);
        model.addAttribute("km", km);
        model.addAttribute("minutes", Math.round(minutes));
        model.addAttribute("minCost", fares.cheapestTotal(solo));
        model.addAttribute("maxCost", fares.priciestTotal(solo));
        model.addAttribute("peak", fares.isPeak(ride.getDepartureTime()));
        return "searching";
    }

    /** Polled by the searching page every few seconds. Shows first names only, no contact info yet. */
    @GetMapping("/api/matches/{id}")
    @ResponseBody
    public Map<String, Object> matchesJson(@PathVariable Long id, HttpSession session) {
        Map<String, Object> out = new LinkedHashMap<>();
        RideRequest ride = ownRide(id, session).orElse(null);
        List<Map<String, Object>> riders = new ArrayList<>();
        if (ride != null) {
            for (MatchResult m : matcher.findMatches(ride)) {
                Map<String, Object> r = new LinkedHashMap<>();
                String full = m.getUser().getName();
                r.put("name", full.contains(" ") ? full.substring(0, full.indexOf(' ')) : full);
                r.put("lat", m.getRequest().getFromLat());
                r.put("lng", m.getRequest().getFromLng());
                r.put("pickupKm", Math.round(m.getPickupKm() * 10) / 10.0);
                r.put("rating", m.getAvgRating() == null ? null : Math.round(m.getAvgRating() * 10) / 10.0);
                riders.add(r);
            }
        }
        out.put("count", riders.size());
        out.put("riders", riders);
        return out;
    }

    /** Demo helper: creates pretend riders near this request. */
    @PostMapping("/api/demo/{id}")
    @ResponseBody
    public Map<String, Object> addDemo(@PathVariable Long id, HttpSession session) {
        RideRequest ride = ownRide(id, session).orElse(null);
        int added = ride == null ? 0 : demo.addDemoRiders(ride, 3);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("added", added);
        return out;
    }

    // ---------- Step 3: matches + fare comparison ----------

    @GetMapping("/results/{id}")
    public String results(@PathVariable Long id, HttpSession session, Model model) {
        RideRequest ride = ownRide(id, session).orElse(null);
        if (ride == null) {
            return "redirect:/request";
        }

        List<MatchResult> matches = matcher.findMatches(ride);
        int groupSize = Math.min(matches.size() + 1, 4);

        double km = geo.roadDistanceKm(ride.getFromLat(), ride.getFromLng(), ride.getToLat(), ride.getToLng());
        double minutes = geo.durationMinutes(km);

        List<FareQuote> quotes = fares.quotes(km, minutes, ride.getDepartureTime(), groupSize);
        List<FareQuote> soloQuotes = fares.quotes(km, minutes, ride.getDepartureTime(), 1);
        FareQuote best = quotes.get(0);
        long soloBest = fares.cheapestTotal(soloQuotes);
        long saving = Math.max(0, soloBest - best.getPerPerson());

        model.addAttribute("ride", ride);
        model.addAttribute("matches", matches);
        model.addAttribute("groupSize", groupSize);
        model.addAttribute("quotes", quotes);
        model.addAttribute("best", best);
        model.addAttribute("soloBest", soloBest);
        model.addAttribute("saving", saving);
        model.addAttribute("km", km);
        model.addAttribute("minutes", Math.round(minutes));
        model.addAttribute("peak", fares.isPeak(ride.getDepartureTime()));
        return "results";
    }

    @PostMapping("/rate")
    public String rate(@RequestParam Long rideId,
                       @RequestParam Long ratedUserId,
                       @RequestParam int stars,
                       HttpSession session) {
        if (ownRide(rideId, session).isPresent()) {
            matcher.rate(currentUserId(session), ratedUserId, stars);
        }
        return "redirect:/results/" + rideId;
    }

    @PostMapping("/cancel/{id}")
    public String cancel(@PathVariable Long id, HttpSession session) {
        ownRide(id, session).ifPresent(r -> {
            r.setStatus(RideStatus.CANCELLED);
            rides.save(r);
        });
        return "redirect:/request";
    }

    // ---------- helpers ----------

    private Long currentUserId(HttpSession session) {
        return (Long) session.getAttribute(AuthInterceptor.SESSION_USER_ID);
    }

    /** Loads a ride only if it belongs to the logged-in user. */
    private Optional<RideRequest> ownRide(Long id, HttpSession session) {
        Long uid = currentUserId(session);
        return rides.findById(id).filter(r -> r.getUser().getId().equals(uid));
    }
}
