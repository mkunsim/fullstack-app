package com.example.safari.booking;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByCustomerEmailOrderByCreatedAtDesc(String email);
    List<Booking> findAllByOrderByCreatedAtDesc();
    Optional<Booking> findByActionToken(String actionToken);
    List<Booking> findByDepartureId(Long departureId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.actionToken = :token")
    Optional<Booking> findByActionTokenForUpdate(@Param("token") String token);

    List<Booking> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            BookingStatus status, Instant createdAt);

    List<Booking> findTop100ByStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
            BookingStatus status, Instant start, Instant end);
}
