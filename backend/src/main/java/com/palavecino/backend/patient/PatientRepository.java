package com.palavecino.backend.patient;

import com.palavecino.backend.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByUser(User user);
}
