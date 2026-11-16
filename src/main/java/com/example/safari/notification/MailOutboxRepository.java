package com.example.safari.notification;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MailOutboxRepository extends JpaRepository<MailOutboxMessage, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    @Modifying
    @Query("update MailOutboxMessage message set message.status = :pending "
            + "where message.status = :sending and message.nextAttemptAt < :now")
    int releaseExpiredClaims(
            @Param("pending") MailOutboxStatus pending,
            @Param("sending") MailOutboxStatus sending,
            @Param("now") Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<MailOutboxMessage> findTop20ByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(
            MailOutboxStatus status,
            Instant now
    );
}
