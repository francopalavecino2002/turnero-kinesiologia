package com.palavecino.backend.service;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<Service, Long> {

    List<Service> findByActiveTrue();
}
