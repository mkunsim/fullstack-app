package com.example.safari.tour;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface DepartureRepository extends JpaRepository<Departure,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Departure d join fetch d.tour where d.id = :id")
    Optional<Departure> findByIdForUpdate(@Param("id") Long id);
    @Query("select d from Departure d join fetch d.tour where d.tour.id = :tourId and d.active = true order by d.startDate")
    List<Departure> findActiveForTour(@Param("tourId") Long tourId);
}
