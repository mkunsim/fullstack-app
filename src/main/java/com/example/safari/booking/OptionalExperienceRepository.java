
package com.example.safari.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OptionalExperienceRepository extends JpaRepository<OptionalExperience, Long> {

    List<OptionalExperience> findAllByOrderByIdAsc();

    List<OptionalExperience> findByAvailableTrueOrderByIdAsc();
}
