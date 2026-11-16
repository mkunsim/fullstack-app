
package com.example.safari.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AddOnRepository extends JpaRepository<AddOn, Long> {

    List<AddOn> findAllByOrderByIdAsc();

    List<AddOn> findByAvailableTrueOrderByIdAsc();
}
