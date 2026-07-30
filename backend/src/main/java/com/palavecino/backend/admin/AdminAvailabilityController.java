package com.palavecino.backend.admin;

import com.palavecino.backend.availability.Availability;
import com.palavecino.backend.availability.AvailabilityRepository;
import com.palavecino.backend.availability.dto.AvailabilityResponse;
import com.palavecino.backend.availability.dto.CreateAvailabilityRequest;
import com.palavecino.backend.availability.dto.UpdateAvailabilityRequest;
import com.palavecino.backend.exception.BusinessRuleViolationException;
import com.palavecino.backend.exception.ResourceNotFoundException;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.service.Service;
import com.palavecino.backend.service.ServiceRepository;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/availability")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAvailabilityController {

    private final AvailabilityRepository availabilityRepository;
    private final ProfessionalRepository professionalRepository;
    private final ServiceRepository serviceRepository;

    public AdminAvailabilityController(AvailabilityRepository availabilityRepository,
                                        ProfessionalRepository professionalRepository,
                                        ServiceRepository serviceRepository) {
        this.availabilityRepository = availabilityRepository;
        this.professionalRepository = professionalRepository;
        this.serviceRepository = serviceRepository;
    }

    @GetMapping
    public ResponseEntity<List<AvailabilityResponse>> list(
            @RequestParam Long professionalId) {
        Professional professional = professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Professional not found with id " + professionalId));

        List<Availability> availabilities = availabilityRepository.findByProfessionalId(professionalId);

        List<AvailabilityResponse> response = availabilities.stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<AvailabilityResponse> create(@Valid @RequestBody CreateAvailabilityRequest request) {
        Professional professional = professionalRepository.findById(request.professionalId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Professional not found with id " + request.professionalId()));

        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessRuleViolationException("startTime must be before endTime");
        }

        Service service = null;
        if (request.serviceId() != null) {
            service = serviceRepository.findById(request.serviceId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Service not found with id " + request.serviceId()));

            if (!service.isActive()) {
                throw new BusinessRuleViolationException(
                        "Service '" + service.getName() + "' is not active");
            }

            if (!professional.getServices().contains(service)) {
                throw new BusinessRuleViolationException(
                        "Professional '" + professional.getFirstName() + " " + professional.getLastName()
                                + "' does not offer service '" + service.getName() + "'");
            }
        }

        Availability availability = new Availability(professional, request.dayOfWeek(),
                request.startTime(), request.endTime(), service);
        availability = availabilityRepository.save(availability);

        return ResponseEntity.created(URI.create("/api/admin/availability/" + availability.getId()))
                .body(toResponse(availability));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AvailabilityResponse> update(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateAvailabilityRequest request) {
        Availability availability = availabilityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Availability not found with id " + id));

        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessRuleViolationException("startTime must be before endTime");
        }

        Professional professional = availability.getProfessional();

        Service service = null;
        if (request.serviceId() != null) {
            service = serviceRepository.findById(request.serviceId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Service not found with id " + request.serviceId()));

            if (!service.isActive()) {
                throw new BusinessRuleViolationException(
                        "Service '" + service.getName() + "' is not active");
            }

            if (!professional.getServices().contains(service)) {
                throw new BusinessRuleViolationException(
                        "Professional '" + professional.getFirstName() + " " + professional.getLastName()
                                + "' does not offer service '" + service.getName() + "'");
            }
        }

        availability.setDayOfWeek(request.dayOfWeek());
        availability.setStartTime(request.startTime());
        availability.setEndTime(request.endTime());
        availability.setService(service);
        availability = availabilityRepository.save(availability);

        return ResponseEntity.ok(toResponse(availability));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!availabilityRepository.existsById(id)) {
            throw new ResourceNotFoundException("Availability not found with id " + id);
        }
        availabilityRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private AvailabilityResponse toResponse(Availability availability) {
        return new AvailabilityResponse(
                availability.getId(),
                availability.getProfessional().getId(),
                availability.getProfessional().getFirstName() + " " + availability.getProfessional().getLastName(),
                availability.getDayOfWeek(),
                availability.getStartTime(),
                availability.getEndTime(),
                availability.getService() != null ? availability.getService().getId() : null,
                availability.getService() != null ? availability.getService().getName() : null
        );
    }
}
