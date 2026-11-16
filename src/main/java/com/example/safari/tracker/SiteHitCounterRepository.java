package com.example.safari.tracker;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SiteHitCounterRepository extends JpaRepository<SiteHitCounter, Short> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select counter from SiteHitCounter counter where counter.id = :id")
    Optional<SiteHitCounter> findByIdForUpdate(@Param("id") short id);
}
