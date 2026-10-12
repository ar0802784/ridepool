package com.ridepool.repo;

import com.ridepool.model.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {
    Optional<PasswordResetOtp> findFirstByUserIdAndUsedFalseOrderByIdDesc(Long userId);
    List<PasswordResetOtp> findByUserIdAndUsedFalse(Long userId);
}
