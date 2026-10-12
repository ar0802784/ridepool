package com.ridepool.repo;

import com.ridepool.model.RideRequest;
import com.ridepool.model.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface RideRequestRepository extends JpaRepository<RideRequest, Long> {
    List<RideRequest> findByStatusAndUserIdNotAndCreatedAtAfter(
            RideStatus status, Long userId, LocalDateTime createdAfter);

    List<RideRequest> findByUserIdAndStatusAndCreatedAtAfter(
            Long userId, RideStatus status, LocalDateTime createdAfter);

    List<RideRequest> findByUserIdOrderByCreatedAtDesc(Long userId);
}
