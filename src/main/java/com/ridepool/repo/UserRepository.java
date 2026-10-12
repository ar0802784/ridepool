package com.ridepool.repo;

import com.ridepool.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    /** Matches a phone number ignoring spaces, dashes and '+', by its trailing digits (e.g. last 10). */
    @Query("select u from User u where replace(replace(replace(replace(u.phone, ' ', ''), '-', ''), '+', ''), '(', '') like concat('%', :digits)")
    List<User> findByPhoneEndingWith(@Param("digits") String digits);
}
