package com.ridepool.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Estimates what each ride-hailing app would roughly charge.
 *
 * IMPORTANT: Ola, Uber, Rapido and inDrive do not publish public fare APIs, so these are
 * ESTIMATES from a simple formula:  fare = max(minFare, base + perKm * km + perMin * minutes) * surge.
 * Edit the RATES list below to tune the numbers. Say "estimated" in your report and demo.
 */
@Service
public class FareService {

    private static final int MAX_CAB_SHARE = 4;

    private static class Rate {
        final String provider;
        final String vehicle;
        final double base;
        final double perKm;
        final double perMin;
        final double minFare;
        final double peakSurge;
        final boolean shareable;
        final String note;

        Rate(String provider, String vehicle, double base, double perKm, double perMin,
             double minFare, double peakSurge, boolean shareable, String note) {
            this.provider = provider;
            this.vehicle = vehicle;
            this.base = base;
            this.perKm = perKm;
            this.perMin = perMin;
            this.minFare = minFare;
            this.peakSurge = peakSurge;
            this.shareable = shareable;
            this.note = note;
        }
    }

    // provider, vehicle, base, perKm, perMin, minFare, peakSurge, shareable, note
    private static final List<Rate> RATES = List.of(
            new Rate("Rapido", "Bike taxi", 20, 7.5, 0.4, 35, 1.10, false,
                    "Carries one rider, so the fare can't be split."),
            new Rate("Ola", "Mini cab", 50, 12.0, 1.2, 90, 1.25, true,
                    "Seats up to 4, so the fare can be split."),
            new Rate("Uber", "Go cab", 45, 12.5, 1.3, 90, 1.30, true,
                    "Seats up to 4, so the fare can be split."),
            new Rate("inDrive", "Cab", 40, 10.5, 1.0, 80, 1.00, true,
                    "Bid-based: this is a typical starting offer. Drivers may counter.")
    );

    public boolean isPeak(LocalDateTime departure) {
        int h = departure.getHour();
        return (h >= 8 && h < 10) || (h >= 17 && h < 20);
    }

    /**
     * @param groupSize how many people will ride together (1 = riding alone)
     * @return quotes sorted by cost per person; the first one is flagged as the best deal
     */
    public List<FareQuote> quotes(double km, double minutes, LocalDateTime departure, int groupSize) {
        boolean peak = isPeak(departure);
        List<FareQuote> list = new ArrayList<>();

        for (Rate r : RATES) {
            double raw = Math.max(r.minFare, r.base + r.perKm * km + r.perMin * minutes);
            double surge = peak ? r.peakSurge : 1.0;
            long total = Math.round(raw * surge);

            int sharers = r.shareable ? Math.min(Math.max(groupSize, 1), MAX_CAB_SHARE) : 1;
            long perPerson = Math.round((double) total / sharers);

            list.add(new FareQuote(r.provider, r.vehicle, total, perPerson,
                    r.shareable, peak && r.peakSurge > 1.0, r.note));
        }

        list.sort(Comparator.comparingLong(FareQuote::getPerPerson));
        if (!list.isEmpty()) {
            list.get(0).setBest(true);
        }
        return list;
    }

    public long cheapestTotal(List<FareQuote> quotes) {
        return quotes.stream().mapToLong(FareQuote::getTotalFare).min().orElse(0);
    }

    public long priciestTotal(List<FareQuote> quotes) {
        return quotes.stream().mapToLong(FareQuote::getTotalFare).max().orElse(0);
    }
}
