package com.ridepool.service;

import com.ridepool.model.RideRequest;
import com.ridepool.model.User;

import java.time.LocalDateTime;

/** A co-rider who is heading the same way at about the same time. */
public class MatchResult {

    private final RideRequest request;
    private final double pickupKm;
    private final double dropKm;
    private final long minutesApart;
    private final Double avgRating;
    private final long ratingCount;
    private final Integer myStars;

    public MatchResult(RideRequest request, double pickupKm, double dropKm, long minutesApart,
                       Double avgRating, long ratingCount, Integer myStars) {
        this.request = request;
        this.pickupKm = pickupKm;
        this.dropKm = dropKm;
        this.minutesApart = minutesApart;
        this.avgRating = avgRating;
        this.ratingCount = ratingCount;
        this.myStars = myStars;
    }

    public RideRequest getRequest() { return request; }
    public User getUser() { return request.getUser(); }
    public double getPickupKm() { return pickupKm; }
    public double getDropKm() { return dropKm; }
    public long getMinutesApart() { return minutesApart; }
    public Double getAvgRating() { return avgRating; }
    public long getRatingCount() { return ratingCount; }
    public Integer getMyStars() { return myStars; }
    public LocalDateTime getDepartureTime() { return request.getDepartureTime(); }
}
