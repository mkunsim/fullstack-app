
package com.example.safari.tour;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TourRepository extends JpaRepository<Tour, Long> {

    @EntityGraph(attributePaths = {"departures"})
    List<Tour> findByActiveTrueOrderByIdAsc();

    @EntityGraph(attributePaths = {"addOns"})
    List<Tour> findAllByOrderByIdAsc();
}
