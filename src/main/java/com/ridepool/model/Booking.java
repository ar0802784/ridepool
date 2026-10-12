package com.ridepool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** The ride service a rider chose on the results page. */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private RideRequest ride;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String vehicle;

    private long totalFare;
    private long perPerson;
    private int groupSize;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public RideRequest getRide() { return ride; }
    public void setRide(RideRequest ride) { this.ride = ride; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getVehicle() { return vehicle; }
    public void setVehicle(String vehicle) { this.vehicle = vehicle; }

    public long getTotalFare() { return totalFare; }
    public void setTotalFare(long totalFare) { this.totalFare = totalFare; }

    public long getPerPerson() { return perPerson; }
    public void setPerPerson(long perPerson) { this.perPerson = perPerson; }

    public int getGroupSize() { return groupSize; }
    public void setGroupSize(int groupSize) { this.groupSize = groupSize; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
