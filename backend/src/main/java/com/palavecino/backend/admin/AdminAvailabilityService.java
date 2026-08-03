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
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
@Transactional(readOnly = true)
public class AdminAvailabilityService {

    private final AvailabilityRepository availabilityRepository;
    private final ProfessionalRepository professionalRepository;
    private final ServiceRepository serviceRepository;

    public AdminAvailabilityService(AvailabilityRepository availabilityRepository,
                                    ProfessionalRepository professionalRepository,
                                    ServiceRepository serviceRepository) {
        this.availabilityRepository = availabilityRepository;
        this.professionalRepository = professionalRepository;
        this.serviceRepository = serviceRepository;
    }

    public List<AvailabilityResponse> list(Long professionalId) {
        professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Professional not found with id " + professionalId));

        return availabilityRepository.findByProfessionalId(professionalId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AvailabilityResponse create(CreateAvailabilityRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessRuleViolationException("startTime must be before endTime");
        }

        Professional professional = professionalRepository.findById(request.professionalId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Professional not found with id " + request.professionalId()));

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

        return toResponse(availability);
    }

    @Transactional
    public AvailabilityResponse update(Long id, UpdateAvailabilityRequest request) {
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

        return toResponse(availability);
    }

    @Transactional
    public void delete(Long id) {
        if (!availabilityRepository.existsById(id)) {
            throw new ResourceNotFoundException("Availability not found with id " + id);
        }
        availabilityRepository.deleteById(id);
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
