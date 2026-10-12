package com.ridepool.repo;

import com.ridepool.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    @Query("select avg(r.stars) from Rating r where r.rated.id = :userId")
    Double averageFor(@Param("userId") Long userId);

    long countByRatedId(Long userId);

    Optional<Rating> findByRaterIdAndRatedId(Long raterId, Long ratedId);
}
