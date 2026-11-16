package com.example.safari.car;
import com.example.safari.security.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
public interface CarBookingRepository extends JpaRepository<CarBooking,Long> {
    @Query("select count(b) from CarBooking b where b.car.id = :carId and b.status not in ('CANCELLED', 'DECLINED') and b.pickupDate < :endDate and b.dropoffDate > :startDate")
    long countOverlaps(@Param("carId") long carId, @Param("startDate") LocalDate startDate,
                       @Param("endDate") LocalDate endDate);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from CarBooking b where b.id = :id")
    java.util.Optional<CarBooking> findByIdForUpdate(@Param("id") long id);
    List<CarBooking> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            String status, LocalDateTime createdAt);
    List<CarBooking> findTop100ByStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
            String status, LocalDateTime start, LocalDateTime end);
    List<CarBooking> findByCustomerOrderByCreatedAtDesc(AppUser customer);
    List<CarBooking> findByCustomerEmailOrderByCreatedAtDesc(String email);
    List<CarBooking> findAllByOrderByCreatedAtDesc();
}
