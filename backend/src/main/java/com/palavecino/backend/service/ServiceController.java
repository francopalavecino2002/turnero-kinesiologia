package com.palavecino.backend.service;

import com.palavecino.backend.exception.ResourceNotFoundException;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.professional.dto.ProfessionalMapper;
import com.palavecino.backend.professional.dto.ProfessionalResponse;
import com.palavecino.backend.service.dto.ServiceMapper;
import com.palavecino.backend.service.dto.ServiceResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only service catalog. Both endpoints are public: a prospective patient browsing before
 * signing up should be able to see what's on offer and who provides it. Creating/editing services
 * is out of scope here - that's the admin panel, a separate write-side feature.
 */
@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceRepository serviceRepository;
    private final ProfessionalRepository professionalRepository;

    public ServiceController(ServiceRepository serviceRepository, ProfessionalRepository professionalRepository) {
        this.serviceRepository = serviceRepository;
        this.professionalRepository = professionalRepository;
    }

    @GetMapping
    public ResponseEntity<List<ServiceResponse>> getActiveServices() {
        List<ServiceResponse> services = serviceRepository.findByActiveTrue().stream()
                .map(ServiceMapper::toResponse)
                .toList();
        return ResponseEntity.ok(services);
    }

    @GetMapping("/{serviceId}/professionals")
    public ResponseEntity<List<ProfessionalResponse>> getProfessionalsForService(@PathVariable Long serviceId) {
        if (!serviceRepository.existsById(serviceId)) {
            throw new ResourceNotFoundException("Service not found with id " + serviceId);
        }

        List<ProfessionalResponse> professionals = professionalRepository.findByServiceId(serviceId).stream()
                .map(ProfessionalMapper::toResponse)
                .toList();
        return ResponseEntity.ok(professionals);
    }
}
