package com.ridepool.service;

import com.ridepool.model.PasswordResetOtp;
import com.ridepool.model.User;
import com.ridepool.repo.PasswordResetOtpRepository;
import com.ridepool.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 30;

    private final UserRepository users;
    private final PasswordResetOtpRepository otps;
    private final PasswordEncoder encoder;
    private final OtpDeliveryService delivery;

    @Value("${ridepool.reset.otp-minutes:10}")
    private long otpMinutes;

    public PasswordResetService(UserRepository users, PasswordResetOtpRepository otps,
                                PasswordEncoder encoder, OtpDeliveryService delivery) {
        this.users = users;
        this.otps = otps;
        this.encoder = encoder;
        this.delivery = delivery;
    }

    /** Finds the account by email (if it contains '@') or by phone number. */
    public Optional<User> findUser(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String id = identifier.trim();
        if (id.contains("@")) {
            return users.findByEmailIgnoreCase(id);
        }
        String digits = id.replaceAll("\\D", "");
        if (digits.length() < 10) {
            return Optional.empty();
        }
        String last10 = digits.substring(digits.length() - 10);
        List<User> matches = users.findByPhoneEndingWith(last10);
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    /** Sends an OTP. Does nothing (silently) if no account matches, so accounts can't be probed. */
    @Transactional
    public void requestOtp(String identifier) {
        Optional<User> found = findUser(identifier);
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();

        Optional<PasswordResetOtp> latest = otps.findFirstByUserIdAndUsedFalseOrderByIdDesc(user.getId());
        if (latest.isPresent()
                && latest.get().getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
            return; // too soon, ignore
        }

        List<PasswordResetOtp> old = otps.findByUserIdAndUsedFalse(user.getId());
        old.forEach(o -> o.setUsed(true));
        otps.saveAll(old);

        String otp = String.valueOf(100000 + RANDOM.nextInt(900000));
        PasswordResetOtp entity = new PasswordResetOtp();
        entity.setUserId(user.getId());
        entity.setOtpHash(encoder.encode(otp));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(otpMinutes));
        otps.save(entity);

        if (identifier.contains("@")) {
            delivery.sendEmail(user.getEmail(), user.getName(), otp, otpMinutes);
        } else {
            delivery.sendSms(user.getPhone(), otp, otpMinutes);
        }
    }

    /** @return the user id if the code is right, empty otherwise. */
    @Transactional
    public Optional<Long> verifyOtp(String identifier, String code) {
        Optional<User> user = findUser(identifier);
        if (user.isEmpty() || code == null) {
            return Optional.empty();
        }
        Optional<PasswordResetOtp> found = otps.findFirstByUserIdAndUsedFalseOrderByIdDesc(user.get().getId());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PasswordResetOtp otp = found.get();
        if (otp.getExpiresAt().isBefore(LocalDateTime.now()) || otp.getAttempts() >= MAX_ATTEMPTS) {
            otp.setUsed(true);
            otps.save(otp);
            return Optional.empty();
        }
        otp.setAttempts(otp.getAttempts() + 1);
        if (encoder.matches(code.trim(), otp.getOtpHash())) {
            otp.setUsed(true);
            otps.save(otp);
            return Optional.of(user.get().getId());
        }
        otps.save(otp);
        return Optional.empty();
    }

    @Transactional
    public boolean resetPassword(Long userId, String newPassword) {
        Optional<User> user = users.findById(userId);
        if (user.isEmpty()) {
            return false;
        }
        user.get().setPasswordHash(encoder.encode(newPassword));
        users.save(user.get());
        return true;
    }
}
