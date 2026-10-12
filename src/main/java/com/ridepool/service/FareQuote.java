package com.ridepool.service;

/** One provider's estimated fare for a trip. */
public class FareQuote {

    private final String provider;
    private final String vehicle;
    private final long totalFare;
    private final long perPerson;
    private final boolean shareable;
    private final boolean surge;
    private final String note;
    private boolean best;

    public FareQuote(String provider, String vehicle, long totalFare, long perPerson,
                     boolean shareable, boolean surge, String note) {
        this.provider = provider;
        this.vehicle = vehicle;
        this.totalFare = totalFare;
        this.perPerson = perPerson;
        this.shareable = shareable;
        this.surge = surge;
        this.note = note;
    }

    public String getProvider() { return provider; }
    public String getVehicle() { return vehicle; }
    public long getTotalFare() { return totalFare; }
    public long getPerPerson() { return perPerson; }
    public boolean isShareable() { return shareable; }
    public boolean isSurge() { return surge; }
    public String getNote() { return note; }
    public boolean isBest() { return best; }
    public void setBest(boolean best) { this.best = best; }
}
