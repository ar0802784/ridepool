package com.ridepool.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ratings")
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User rater;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User rated;

    @Column(nullable = false)
    private int stars;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getRater() { return rater; }
    public void setRater(User rater) { this.rater = rater; }

    public User getRated() { return rated; }
    public void setRated(User rated) { this.rated = rated; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }
}
