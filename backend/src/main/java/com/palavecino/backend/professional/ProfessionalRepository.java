package com.palavecino.backend.professional;

import com.palavecino.backend.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfessionalRepository extends JpaRepository<Professional, Long> {

    Optional<Professional> findByUser(User user);

    Optional<Professional> findByFirstNameAndLastName(String firstName, String lastName);

    // ProfessionalResponse now carries the professional's offered services, so this needs to
    // eager-fetch the (lazy) `services` collection - open-in-view is disabled, so leaving it lazy
    // would throw LazyInitializationException once the mapper touches it. The fetch join uses a
    // separate alias (allServices) from the filtering join (s) so the WHERE clause can still match
    // on a single service while the full collection gets loaded.
    // Only ACTIVE professionals are exposed to the public catalog: a deactivated professional
    // (User.active = false) must not show up as a bookable option for patients.
    @Query("""
            SELECT DISTINCT p FROM Professional p
            JOIN p.services s
            JOIN p.user u
            LEFT JOIN FETCH p.services allServices
            WHERE s.id = :serviceId
              AND u.active = true
            ORDER BY p.firstName, p.lastName
            """)
    List<Professional> findByServiceId(@Param("serviceId") Long serviceId);

    @Query("SELECT p FROM Professional p LEFT JOIN FETCH p.services WHERE p.id = :id")
    Optional<Professional> findByIdFetchingServices(@Param("id") Long id);
}
