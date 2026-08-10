package com.palavecino.backend.patient;

import com.palavecino.backend.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByUser(User user);

    Optional<Patient> findByGuestPhone(String guestPhone);

    @Query("""
            SELECT p FROM Patient p LEFT JOIN p.user u
            WHERE LOWER(p.firstName) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(p.guestEmail) LIKE LOWER(CONCAT('%', :term, '%'))
            ORDER BY p.firstName, p.lastName
            """)
    List<Patient> search(@Param("term") String term, Pageable pageable);
}
