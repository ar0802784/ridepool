package com.ridepool.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Distance and travel-time helpers. No external API needed, so it always works offline. */
@Service
public class GeoService {

    private final double roadFactor;
    private final double avgSpeedKmph;

    public GeoService(@Value("${ridepool.geo.road-factor:1.3}") double roadFactor,
                      @Value("${ridepool.geo.avg-speed-kmph:28}") double avgSpeedKmph) {
        this.roadFactor = roadFactor;
        this.avgSpeedKmph = avgSpeedKmph;
    }

    /** Great-circle distance between two points, in km (Haversine formula). */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Estimated driving distance: straight-line distance scaled by a road factor. */
    public double roadDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double km = haversineKm(lat1, lon1, lat2, lon2) * roadFactor;
        return Math.max(km, 0.5);
    }

    /** Estimated travel time in minutes for a given road distance. */
    public double durationMinutes(double roadKm) {
        return roadKm / avgSpeedKmph * 60.0;
    }
}
