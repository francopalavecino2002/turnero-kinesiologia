package com.palavecino.backend.availability;

import com.palavecino.backend.professional.Professional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {

    List<Availability> findByProfessionalAndDayOfWeek(Professional professional, DayOfWeek dayOfWeek);
}
