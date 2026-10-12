package com.ridepool.repo;

import com.ridepool.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Messages between two users with id greater than afterId, oldest first. */
    @Query("select m from Message m "
            + "where ((m.sender.id = :a and m.recipient.id = :b) or (m.sender.id = :b and m.recipient.id = :a)) "
            + "and m.id > :afterId order by m.id asc")
    List<Message> thread(@Param("a") Long a, @Param("b") Long b, @Param("afterId") Long afterId);

    /** Every message the user sent or received, newest first. */
    @Query("select m from Message m where m.sender.id = :u or m.recipient.id = :u order by m.id desc")
    List<Message> involving(@Param("u") Long u);

    @Query("select count(m) from Message m "
            + "where (m.sender.id = :a and m.recipient.id = :b) or (m.sender.id = :b and m.recipient.id = :a)")
    long countBetween(@Param("a") Long a, @Param("b") Long b);

    long countByRecipientIdAndSeenFalse(Long recipientId);

    long countBySenderIdAndRecipientIdAndSeenFalse(Long senderId, Long recipientId);

    @Modifying
    @Query("update Message m set m.seen = true where m.sender.id = :other and m.recipient.id = :me and m.seen = false")
    int markSeen(@Param("other") Long other, @Param("me") Long me);
}
