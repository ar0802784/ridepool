package com.ridepool.service;

import com.ridepool.model.Rating;
import com.ridepool.model.RideRequest;
import com.ridepool.model.User;
import com.ridepool.repo.RatingRepository;
import com.ridepool.repo.RideRequestRepository;
import com.ridepool.repo.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

/**
 * Creates pretend co-riders near a real request, so you can demo matching
 * on a laptop with only one person logged in. Used by the "Add demo riders" button.
 */
@Service
public class DemoService {

    private static final String[] NAMES = {"Aarav", "Diya", "Kabir", "Meera", "Rohan", "Ishita"};
    private static final int[] STARS = {5, 4, 5, 4, 3, 5};
    private static final String COMMUNITY_EMAIL = "community@ridepool.demo";

    private final UserRepository users;
    private final RideRequestRepository requests;
    private final RatingRepository ratings;
    private final PasswordEncoder encoder;
    private final Random random = new Random();

    public DemoService(UserRepository users, RideRequestRepository requests,
                       RatingRepository ratings, PasswordEncoder encoder) {
        this.users = users;
        this.requests = requests;
        this.ratings = ratings;
        this.encoder = encoder;
    }

    @Transactional
    public int addDemoRiders(RideRequest base, int count) {
        User community = users.findByEmailIgnoreCase(COMMUNITY_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setName("RidePool Community");
            u.setEmail(COMMUNITY_EMAIL);
            u.setPhone("-");
            u.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
            return users.save(u);
        });

        int made = Math.min(count, NAMES.length);
        for (int i = 0; i < made; i++) {
            User u = new User();
            u.setName(NAMES[i] + " (demo)");
            u.setEmail("demo-" + UUID.randomUUID().toString().substring(0, 8) + "@ridepool.demo");
            u.setPhone("+91 00000 0000" + (i + 1));
            u.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
            u = users.save(u);

            // ~100-700 m away from the real pickup and drop points
            RideRequest r = new RideRequest();
            r.setUser(u);
            r.setFromName(base.getFromName());
            r.setFromLat(base.getFromLat() + jitter());
            r.setFromLng(base.getFromLng() + jitter());
            r.setToName(base.getToName());
            r.setToLat(base.getToLat() + jitter());
            r.setToLng(base.getToLng() + jitter());
            r.setDepartureTime(base.getDepartureTime().plusMinutes(random.nextInt(21) - 10));
            r.setCreatedAt(LocalDateTime.now());
            requests.save(r);

            Rating rating = new Rating();
            rating.setRater(community);
            rating.setRated(u);
            rating.setStars(STARS[i]);
            ratings.save(rating);
        }
        return made;
    }

    /** Random offset of up to ~0.006 degrees (about 650 m). */
    private double jitter() {
        return (random.nextDouble() - 0.5) * 0.012;
    }
}
