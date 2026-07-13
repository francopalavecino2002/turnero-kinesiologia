package com.palavecino.backend.professional;

import com.palavecino.backend.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessionalRepository extends JpaRepository<Professional, Long> {

    Optional<Professional> findByUser(User user);

    Optional<Professional> findByFirstNameAndLastName(String firstName, String lastName);
}
