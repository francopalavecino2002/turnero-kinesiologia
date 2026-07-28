package com.palavecino.backend.service;

import com.palavecino.backend.exception.ConflictException;
import com.palavecino.backend.exception.ResourceNotFoundException;
import com.palavecino.backend.service.dto.ServiceAdminResponse;
import com.palavecino.backend.service.dto.ServiceCreateRequest;
import com.palavecino.backend.service.dto.ServiceMapper;
import com.palavecino.backend.service.dto.ServiceResponse;
import com.palavecino.backend.service.dto.ServiceUpdateRequest;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
@Transactional(readOnly = true)
public class ServiceService {

    private final ServiceRepository serviceRepository;

    public ServiceService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    public List<ServiceAdminResponse> list(boolean includeInactive) {
        List<Service> services = includeInactive
                ? serviceRepository.findAllByOrderByIdAsc()
                : serviceRepository.findByActiveTrue();
        return services.stream()
                .map(ServiceMapper::toAdminResponse)
                .toList();
    }

    public List<ServiceResponse> findActiveServices() {
        return serviceRepository.findByActiveTrue().stream()
                .map(ServiceMapper::toResponse)
                .toList();
    }

    public ServiceAdminResponse findById(Long id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id " + id));
        return ServiceMapper.toAdminResponse(service);
    }

    @Transactional
    public ServiceAdminResponse create(ServiceCreateRequest request) {
        if (serviceRepository.existsByNameIgnoreCaseAndActiveTrue(request.name())) {
            throw new ConflictException(
                    "A service with the name '" + request.name() + "' already exists. "
                            + "If it was deactivated, consider reactivating it instead.");
        }

        Service service = new Service(request.name(), request.durationMinutes(), true);
        service = serviceRepository.save(service);
        return ServiceMapper.toAdminResponse(service);
    }

    /**
     * Updates name and/or durationMinutes. Existing appointments are NOT affected by
     * duration changes because each appointment stores a snapshot (durationMinutes)
     * at booking time — only new bookings use the updated value.
     */
    @Transactional
    public ServiceAdminResponse update(Long id, ServiceUpdateRequest request) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id " + id));

        if (!service.getName().equalsIgnoreCase(request.name())
                && serviceRepository.existsByNameIgnoreCaseAndActiveTrue(request.name())) {
            throw new ConflictException(
                    "A service with the name '" + request.name() + "' already exists.");
        }

        service.setName(request.name());
        service.setDurationMinutes(request.durationMinutes());
        service = serviceRepository.save(service);
        return ServiceMapper.toAdminResponse(service);
    }

    @Transactional
    public ServiceAdminResponse deactivate(Long id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id " + id));
        service.setActive(false);
        service = serviceRepository.save(service);
        return ServiceMapper.toAdminResponse(service);
    }

    @Transactional
    public ServiceAdminResponse reactivate(Long id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id " + id));

        if (service.isActive()) {
            return ServiceMapper.toAdminResponse(service);
        }

        if (serviceRepository.existsByNameIgnoreCaseAndActiveTrue(service.getName())) {
            throw new ConflictException(
                    "Cannot reactivate '" + service.getName()
                            + "': another active service with that name already exists.");
        }

        service.setActive(true);
        service = serviceRepository.save(service);
        return ServiceMapper.toAdminResponse(service);
    }
}
