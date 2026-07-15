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

    // No JOIN FETCH needed here: the query only reads columns off Professional itself (mapped
    // straight to ProfessionalResponse), it never touches the lazy `services` collection, so
    // there's nothing further to fetch and no N+1 risk.
    @Query("""
            SELECT p FROM Professional p
            JOIN p.services s
            WHERE s.id = :serviceId
            ORDER BY p.firstName, p.lastName
            """)
    List<Professional> findByServiceId(@Param("serviceId") Long serviceId);
}
