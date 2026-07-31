package com.palavecino.backend.admin;

import com.palavecino.backend.appointment.AppointmentRepository;
import com.palavecino.backend.availability.DayOfWeek;
import com.palavecino.backend.exception.BusinessRuleViolationException;
import com.palavecino.backend.exception.ResourceNotFoundException;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.recurringblock.RecurringBlock;
import com.palavecino.backend.recurringblock.RecurringBlockRepository;
import com.palavecino.backend.recurringblock.dto.RecurringBlockAdminResponse;
import com.palavecino.backend.recurringblock.dto.RecurringBlockCreateRequest;
import com.palavecino.backend.recurringblock.dto.RecurringBlockUpdateRequest;
import com.palavecino.backend.service.Service;
import com.palavecino.backend.service.ServiceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
@Transactional(readOnly = true)
public class AdminRecurringBlockService {

    private static final int AFFECTED_WEEKS = 12;

    private final RecurringBlockRepository recurringBlockRepository;
    private final ServiceRepository serviceRepository;
    private final ProfessionalRepository professionalRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;
    private final int maxConcurrentAppointments;

    public AdminRecurringBlockService(RecurringBlockRepository recurringBlockRepository,
                                       ServiceRepository serviceRepository,
                                       ProfessionalRepository professionalRepository,
                                       AppointmentRepository appointmentRepository,
                                       Clock clock,
                                       @Value("${clinic.max-concurrent-appointments}") int maxConcurrentAppointments) {
        this.recurringBlockRepository = recurringBlockRepository;
        this.serviceRepository = serviceRepository;
        this.professionalRepository = professionalRepository;
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
        this.maxConcurrentAppointments = maxConcurrentAppointments;
    }

    public List<RecurringBlockAdminResponse> list(boolean includeInactive) {
        return recurringBlockRepository.findAll().stream()
                .filter(rb -> includeInactive || rb.isActive())
                .map(this::toResponse)
                .toList();
    }

    public RecurringBlockAdminResponse findById(Long id) {
        RecurringBlock block = findBlock(id);
        return toResponse(block);
    }

    @Transactional
    public RecurringBlockAdminResponse create(RecurringBlockCreateRequest request) {
        validateTimeOrder(request.startTime(), request.endTime());

        Service service = resolveService(request.serviceId());
        Professional professional = resolveProfessional(request.professionalId());

        checkCapacity(request.dayOfWeek(), request.startTime(), request.endTime(), null);

        RecurringBlock block = new RecurringBlock(
                request.dayOfWeek(), request.startTime(), request.endTime(),
                service, professional, true, request.description());
        block = recurringBlockRepository.save(block);

        int affected = countAffectedAppointments(block);
        return toResponse(block, affected);
    }

    @Transactional
    public RecurringBlockAdminResponse update(Long id, RecurringBlockUpdateRequest request) {
        validateTimeOrder(request.startTime(), request.endTime());

        RecurringBlock block = findBlock(id);

        Service service = resolveService(request.serviceId());
        Professional professional = resolveProfessional(request.professionalId());

        block.setDayOfWeek(request.dayOfWeek());
        block.setStartTime(request.startTime());
        block.setEndTime(request.endTime());
        block.setDescription(request.description());
        block.setService(service);
        block.setProfessional(professional);

        block = recurringBlockRepository.save(block);
        return toResponse(block);
    }

    @Transactional
    public RecurringBlockAdminResponse deactivate(Long id) {
        RecurringBlock block = findBlock(id);
        block.setActive(false);
        block = recurringBlockRepository.save(block);
        return toResponse(block);
    }

    @Transactional
    public RecurringBlockAdminResponse reactivate(Long id) {
        RecurringBlock block = findBlock(id);

        if (block.isActive()) {
            return toResponse(block);
        }

        checkCapacity(block.getDayOfWeek(), block.getStartTime(), block.getEndTime(), id);

        block.setActive(true);
        block = recurringBlockRepository.save(block);

        int affected = countAffectedAppointments(block);
        return toResponse(block, affected);
    }

    private RecurringBlock findBlock(Long id) {
        return recurringBlockRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RecurringBlock not found with id " + id));
    }

    private void validateTimeOrder(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new BusinessRuleViolationException("startTime must be before endTime");
        }
    }

    private Service resolveService(Long serviceId) {
        if (serviceId == null) {
            return null;
        }
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id " + serviceId));
        if (!service.isActive()) {
            throw new BusinessRuleViolationException(
                    "Service '" + service.getName() + "' is not active");
        }
        return service;
    }

    private Professional resolveProfessional(Long professionalId) {
        if (professionalId == null) {
            return null;
        }
        Professional professional = professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Professional not found with id " + professionalId));
        if (!professional.getUser().isActive()) {
            throw new BusinessRuleViolationException(
                    "Professional '" + professional.getFirstName() + " " + professional.getLastName()
                            + "' is not active");
        }
        return professional;
    }

    private void checkCapacity(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, Long excludeBlockId) {
        List<RecurringBlock> overlapping = recurringBlockRepository.findActiveOverlapping(
                dayOfWeek, startTime, endTime);

        long count = overlapping.stream()
                .filter(rb -> excludeBlockId == null || !rb.getId().equals(excludeBlockId))
                .count();

        if (count >= maxConcurrentAppointments) {
            throw new BusinessRuleViolationException(
                    "No se puede agregar el bloque porque en ese horario ya hay "
                    + count + " bloque(s) activo(s) solapado(s). "
                    + "La capacidad máxima del consultorio es de " + maxConcurrentAppointments
                    + " turnos simultáneos, y cada bloque ocupa un box.");
        }
    }

    private int countAffectedAppointments(RecurringBlock block) {
        LocalDate today = LocalDate.now(clock);
        java.time.DayOfWeek targetDow = mapDayOfWeek(block.getDayOfWeek());
        LocalTime startTime = block.getStartTime();
        LocalTime endTime = block.getEndTime();

        int count = 0;
        LocalDate date = today.plusDays(1);
        LocalDate limit = today.plusWeeks(AFFECTED_WEEKS);

        while (!date.isAfter(limit)) {
            if (date.getDayOfWeek() == targetDow) {
                LocalDateTime rangeStart = LocalDateTime.of(date, startTime);
                LocalDateTime rangeEnd = LocalDateTime.of(date, endTime);
                count += appointmentRepository.countOverlappingActive(rangeStart, rangeEnd);
            }
            date = date.plusDays(1);
        }

        return count;
    }

    private static java.time.DayOfWeek mapDayOfWeek(DayOfWeek day) {
        return java.time.DayOfWeek.valueOf(day.name());
    }

    private RecurringBlockAdminResponse toResponse(RecurringBlock block) {
        return toResponse(block, 0);
    }

    private RecurringBlockAdminResponse toResponse(RecurringBlock block, int affectedAppointmentsCount) {
        return new RecurringBlockAdminResponse(
                block.getId(),
                block.getDayOfWeek(),
                block.getStartTime(),
                block.getEndTime(),
                block.getDescription(),
                block.isActive(),
                block.getService() != null ? block.getService().getId() : null,
                block.getService() != null ? block.getService().getName() : null,
                block.getProfessional() != null ? block.getProfessional().getId() : null,
                block.getProfessional() != null
                        ? block.getProfessional().getFirstName() + " " + block.getProfessional().getLastName()
                        : null,
                affectedAppointmentsCount);
    }
}
