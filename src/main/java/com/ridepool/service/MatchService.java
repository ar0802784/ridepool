package com.ridepool.service;

import com.ridepool.model.Rating;
import com.ridepool.model.RideRequest;
import com.ridepool.model.RideStatus;
import com.ridepool.model.User;
import com.ridepool.repo.RatingRepository;
import com.ridepool.repo.RideRequestRepository;
import com.ridepool.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds co-riders. Two requests match when:
 *   1. pickup points are within pickup-radius-km of each other,
 *   2. drop points are within drop-radius-km of each other,
 *   3. departure times are within time-window-minutes of each other.
 */
@Service
public class MatchService {

    private final RideRequestRepository requests;
    private final RatingRepository ratings;
    private final UserRepository users;
    private final double pickupRadiusKm;
    private final double dropRadiusKm;
    private final long timeWindowMinutes;
    private final long requestLifetimeHours;

    public MatchService(RideRequestRepository requests,
                        RatingRepository ratings,
                        UserRepository users,
                        @Value("${ridepool.match.pickup-radius-km:2.0}") double pickupRadiusKm,
                        @Value("${ridepool.match.drop-radius-km:2.0}") double dropRadiusKm,
                        @Value("${ridepool.match.time-window-minutes:20}") long timeWindowMinutes,
                        @Value("${ridepool.match.request-lifetime-hours:3}") long requestLifetimeHours) {
        this.requests = requests;
        this.ratings = ratings;
        this.users = users;
        this.pickupRadiusKm = pickupRadiusKm;
        this.dropRadiusKm = dropRadiusKm;
        this.timeWindowMinutes = timeWindowMinutes;
        this.requestLifetimeHours = requestLifetimeHours;
    }

    @Transactional(readOnly = true)
    public List<MatchResult> findMatches(RideRequest mine) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(requestLifetimeHours);
        List<RideRequest> candidates = requests.findByStatusAndUserIdNotAndCreatedAtAfter(
                RideStatus.OPEN, mine.getUser().getId(), cutoff);

        List<MatchResult> found = new ArrayList<>();
        for (RideRequest other : candidates) {
            double pickupKm = GeoService.haversineKm(
                    mine.getFromLat(), mine.getFromLng(), other.getFromLat(), other.getFromLng());
            double dropKm = GeoService.haversineKm(
                    mine.getToLat(), mine.getToLng(), other.getToLat(), other.getToLng());
            long minutesApart = Math.abs(Duration.between(
                    mine.getDepartureTime(), other.getDepartureTime()).toMinutes());

            if (pickupKm <= pickupRadiusKm && dropKm <= dropRadiusKm && minutesApart <= timeWindowMinutes) {
                Long otherId = other.getUser().getId();
                Double avg = ratings.averageFor(otherId);
                long count = ratings.countByRatedId(otherId);
                Integer myStars = ratings.findByRaterIdAndRatedId(mine.getUser().getId(), otherId)
                        .map(Rating::getStars).orElse(null);
                found.add(new MatchResult(other, pickupKm, dropKm, minutesApart, avg, count, myStars));
            }
        }

        // Best matches first: closest pickup + drop, then better rating
        found.sort(Comparator
                .comparingDouble((MatchResult m) -> m.getPickupKm() + m.getDropKm())
                .thenComparingDouble(m -> -(m.getAvgRating() == null ? 0.0 : m.getAvgRating())));
        return found;
    }

    /** True if any of my open ride requests currently matches an open request from the other user. */
    @Transactional(readOnly = true)
    public boolean areMatched(Long meId, Long otherId) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(requestLifetimeHours);
        for (RideRequest mine : requests.findByUserIdAndStatusAndCreatedAtAfter(meId, RideStatus.OPEN, cutoff)) {
            for (MatchResult m : findMatches(mine)) {
                if (m.getUser().getId().equals(otherId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Save (or update) a 1-5 star rating from one rider to another. */
    @Transactional
    public void rate(Long raterId, Long ratedId, int stars) {
        if (stars < 1 || stars > 5 || raterId.equals(ratedId)) {
            return;
        }
        User rater = users.findById(raterId).orElse(null);
        User rated = users.findById(ratedId).orElse(null);
        if (rater == null || rated == null) {
            return;
        }
        Rating rating = ratings.findByRaterIdAndRatedId(raterId, ratedId).orElseGet(Rating::new);
        rating.setRater(rater);
        rating.setRated(rated);
        rating.setStars(stars);
        ratings.save(rating);
    }
}
