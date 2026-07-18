package com.palavecino.backend.appointment;

import com.palavecino.backend.appointment.dto.AgendaEntryResponse;
import com.palavecino.backend.appointment.dto.AppointmentMapper;
import com.palavecino.backend.appointment.dto.AppointmentResponse;
import com.palavecino.backend.appointment.dto.AvailableSlotResponse;
import com.palavecino.backend.appointment.dto.CreateAppointmentRequest;
import com.palavecino.backend.appointment.dto.MonthSummaryResponse;
import com.palavecino.backend.availability.Availability;
import com.palavecino.backend.availability.AvailabilityRepository;
import com.palavecino.backend.exception.BusinessRuleViolationException;
import com.palavecino.backend.exception.ConflictException;
import com.palavecino.backend.exception.ResourceNotFoundException;
import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.patient.PatientRepository;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.recurringblock.RecurringBlock;
import com.palavecino.backend.recurringblock.RecurringBlockRepository;
import com.palavecino.backend.security.AuthenticatedUser;
import com.palavecino.backend.service.Service;
import com.palavecino.backend.service.ServiceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final ProfessionalRepository professionalRepository;
    private final PatientRepository patientRepository;
    private final ServiceRepository serviceRepository;
    private final AvailabilityRepository availabilityRepository;
    private final RecurringBlockRepository recurringBlockRepository;
    private final Clock clock;
    private final int maxConcurrentAppointments;
    private final long minCancellationHours;

    public AppointmentService(AppointmentRepository appointmentRepository,
                               ProfessionalRepository professionalRepository,
                               PatientRepository patientRepository,
                               ServiceRepository serviceRepository,
                               AvailabilityRepository availabilityRepository,
                               RecurringBlockRepository recurringBlockRepository,
                               Clock clock,
                               @Value("${clinic.max-concurrent-appointments}") int maxConcurrentAppointments,
                               @Value("${clinic.min-cancellation-hours}") long minCancellationHours) {
        this.appointmentRepository = appointmentRepository;
        this.professionalRepository = professionalRepository;
        this.patientRepository = patientRepository;
        this.serviceRepository = serviceRepository;
        this.availabilityRepository = availabilityRepository;
        this.recurringBlockRepository = recurringBlockRepository;
        this.clock = clock;
        this.maxConcurrentAppointments = maxConcurrentAppointments;
        this.minCancellationHours = minCancellationHours;
    }

    public List<AppointmentResponse> findByProfessionalAndDate(Long professionalId, LocalDate date, AuthenticatedUser currentUser) {
        if (currentUser.isProfessional() && !professionalId.equals(currentUser.professionalId())) {
            throw new AccessDeniedException("You can only view your own agenda");
        }

        Professional professional = professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Professional not found with id " + professionalId));

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        return appointmentRepository.findByProfessionalAndDate(professional, dayStart, dayEnd).stream()
                .map(AppointmentMapper::toResponse)
                .toList();
    }

    public List<AppointmentResponse> findMyAppointments(AuthenticatedUser currentUser) {
        Patient patient = requirePatient(currentUser);

        return appointmentRepository.findByPatientWithDetails(patient).stream()
                .map(AppointmentMapper::toResponse)
                .toList();
    }

    public List<AppointmentResponse> findMyAgenda(LocalDate date, AuthenticatedUser currentUser) {
        Professional professional = requireProfessional(currentUser);

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        return appointmentRepository.findByProfessionalAndDate(professional, dayStart, dayEnd).stream()
                .map(AppointmentMapper::toResponse)
                .toList();
    }

    public List<AppointmentResponse> findAllByDate(LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        return appointmentRepository.findAllByDate(dayStart, dayEnd).stream()
                .map(AppointmentMapper::toResponse)
                .toList();
    }

    public List<AgendaEntryResponse> findClinicAgenda(LocalDate date, AuthenticatedUser currentUser) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        List<Appointment> appointments = appointmentRepository.findAllByDate(dayStart, dayEnd);

        Long currentProfessionalId = currentUser.professionalId();

        return appointments.stream()
                .map(appointment -> {
                    boolean isOwn = currentUser.isAdmin()
                            || (currentProfessionalId != null
                                && appointment.getProfessional().getId().equals(currentProfessionalId));
                    return isOwn
                            ? AppointmentMapper.toFullAgendaEntry(appointment)
                            : AppointmentMapper.toReducedAgendaEntry(appointment);
                })
                .toList();
    }

    public MonthSummaryResponse findMonthSummary(int year, int month, AuthenticatedUser currentUser) {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDateTime rangeStart = firstDay.atStartOfDay();
        LocalDateTime rangeEnd = firstDay.plusMonths(1).atStartOfDay();

        List<Object[]> rows;
        if (currentUser.isProfessional()) {
            Professional professional = requireProfessional(currentUser);
            rows = appointmentRepository.countNonCancelledPerDayByProfessional(
                    rangeStart, rangeEnd, professional.getId());
        } else {
            rows = appointmentRepository.countNonCancelledPerDay(rangeStart, rangeEnd);
        }

        List<MonthSummaryResponse.DaySummary> days = rows.stream()
                .map(row -> new MonthSummaryResponse.DaySummary(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).intValue()))
                .toList();

        return new MonthSummaryResponse(days);
    }

    public List<AvailableSlotResponse> findAvailableSlots(Long professionalId, Long serviceId, LocalDate date) {
        Professional professional = professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Professional not found with id " + professionalId));

        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service not found with id " + serviceId));

        if (!professional.getServices().contains(service)) {
            throw new BusinessRuleViolationException(
                    "Professional " + professional.getFirstName() + " " + professional.getLastName()
                            + " does not offer service " + service.getName());
        }

        com.palavecino.backend.availability.DayOfWeek dayOfWeek = toDayOfWeek(date);
        List<Availability> availabilities = availabilityRepository.findByProfessionalAndDayOfWeek(
                professional, dayOfWeek);

        if (availabilities.isEmpty()) {
            return List.of();
        }

        List<AvailableSlotResponse> slots = new ArrayList<>();

        for (Availability availability : availabilities) {
            LocalTime slotStart = availability.getStartTime();
            LocalTime availabilityEnd = availability.getEndTime();
            int durationMinutes = service.getDurationMinutes();

            while (slotStart.plusMinutes(durationMinutes).isBefore(availabilityEnd)
                    || slotStart.plusMinutes(durationMinutes).equals(availabilityEnd)) {
                LocalTime slotEnd = slotStart.plusMinutes(durationMinutes);
                LocalDateTime rangeStart = LocalDateTime.of(date, slotStart);
                LocalDateTime rangeEnd = LocalDateTime.of(date, slotEnd);

                boolean professionalBusy = !appointmentRepository
                        .findOverlappingByProfessional(professional, rangeStart, rangeEnd)
                        .isEmpty();
                boolean capacityFull = checkCapacity(dayOfWeek, service, rangeStart, rangeEnd).full();

                if (!professionalBusy && !capacityFull) {
                    slots.add(new AvailableSlotResponse(rangeStart, rangeEnd));
                }

                slotStart = slotEnd;
            }
        }

        if (date.equals(LocalDate.now(clock))) {
            LocalDateTime now = LocalDateTime.now(clock);
            slots.removeIf(slot -> slot.startTime().isBefore(now));
        }

        return slots;
    }

    public AppointmentResponse findById(Long id, AuthenticatedUser currentUser) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id " + id));

        requireOwnership(appointment, currentUser);

        return AppointmentMapper.toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse bookAppointment(CreateAppointmentRequest request, AuthenticatedUser currentUser) {
        Patient patient = requirePatient(currentUser);

        Professional professional = professionalRepository.findById(request.professionalId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Professional not found with id " + request.professionalId()));

        Service service = serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service not found with id " + request.serviceId()));

        if (!professional.getServices().contains(service)) {
            throw new BusinessRuleViolationException(
                    "Professional " + professional.getFirstName() + " " + professional.getLastName()
                            + " does not offer service " + service.getName());
        }

        LocalDateTime dateTime = request.dateTime();
        LocalDateTime rangeEnd = dateTime.plusMinutes(service.getDurationMinutes());

        com.palavecino.backend.availability.DayOfWeek dayOfWeek = toDayOfWeek(dateTime.toLocalDate());
        List<Availability> availabilities = availabilityRepository.findByProfessionalAndDayOfWeek(
                professional, dayOfWeek);

        boolean fitsWithinAvailability = availabilities.stream().anyMatch(availability ->
                !dateTime.toLocalTime().isBefore(availability.getStartTime())
                        && !rangeEnd.toLocalTime().isAfter(availability.getEndTime()));

        if (!fitsWithinAvailability) {
            throw new BusinessRuleViolationException(
                    "Requested time " + dateTime + " is outside " + professional.getFirstName() + " "
                            + professional.getLastName() + "'s availability");
        }

        boolean professionalBusy = !appointmentRepository
                .findOverlappingByProfessional(professional, dateTime, rangeEnd)
                .isEmpty();
        if (professionalBusy) {
            throw new ConflictException(
                    "Professional " + professional.getFirstName() + " " + professional.getLastName()
                            + " already has an appointment overlapping " + dateTime);
        }

        CapacityCheckResult capacity = checkCapacity(dayOfWeek, service, dateTime, rangeEnd);
        if (capacity.full()) {
            if (capacity.reservedBlockBox()) {
                throw new ConflictException(
                        "The reserved slot for " + service.getName() + " at " + dateTime + " is already taken");
            }
            throw new ConflictException("The clinic is at full capacity at " + dateTime);
        }

        Appointment appointment = new Appointment(patient, professional, service, dateTime, AppointmentStatus.BOOKED);
        appointment = appointmentRepository.save(appointment);

        return AppointmentMapper.toResponse(appointmentRepository.findByIdWithDetails(appointment.getId())
                .orElseThrow());
    }

    @Transactional
    public AppointmentResponse cancel(Long id, AuthenticatedUser currentUser) {
        Appointment appointment = findOwnedForTransition(id, currentUser);

        ensureTransitionAllowed(appointment, AppointmentStatus.CANCELLED);

        if (currentUser.isPatient()) {
            long minutesUntilAppointment = Duration.between(LocalDateTime.now(clock), appointment.getDateTime()).toMinutes();
            if (minutesUntilAppointment < minCancellationHours * 60) {
                throw new ConflictException(
                        "Appointments can only be cancelled at least " + minCancellationHours
                                + " hours before the scheduled time");
            }
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        return AppointmentMapper.toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse confirm(Long id, AuthenticatedUser currentUser) {
        return transitionAsStaff(id, currentUser, AppointmentStatus.CONFIRMED);
    }

    @Transactional
    public AppointmentResponse complete(Long id, AuthenticatedUser currentUser) {
        return transitionAsStaff(id, currentUser, AppointmentStatus.COMPLETED);
    }

    @Transactional
    public AppointmentResponse noShow(Long id, AuthenticatedUser currentUser) {
        return transitionAsStaff(id, currentUser, AppointmentStatus.NO_SHOW);
    }

    private AppointmentResponse transitionAsStaff(Long id, AuthenticatedUser currentUser, AppointmentStatus target) {
        Appointment appointment = findOwnedForTransition(id, currentUser);
        ensureTransitionAllowed(appointment, target);
        appointment.setStatus(target);
        return AppointmentMapper.toResponse(appointment);
    }

    private void ensureTransitionAllowed(Appointment appointment, AppointmentStatus target) {
        if (!appointment.getStatus().canTransitionTo(target)) {
            throw new ConflictException(
                    "Cannot transition appointment from " + appointment.getStatus() + " to " + target);
        }
    }

    private Appointment findOwnedForTransition(Long id, AuthenticatedUser currentUser) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id " + id));

        requireOwnership(appointment, currentUser);

        return appointment;
    }

    private void requireOwnership(Appointment appointment, AuthenticatedUser currentUser) {
        boolean owns = currentUser.isAdmin()
                || (currentUser.isPatient() && appointment.getPatient().getId().equals(currentUser.patientId()))
                || (currentUser.isProfessional() && appointment.getProfessional().getId().equals(currentUser.professionalId()));

        if (!owns) {
            throw new AccessDeniedException("You do not have access to this appointment");
        }
    }

    private Patient requirePatient(AuthenticatedUser currentUser) {
        return patientRepository.findById(currentUser.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient record not found for current user"));
    }

    private Professional requireProfessional(AuthenticatedUser currentUser) {
        return professionalRepository.findById(currentUser.professionalId())
                .orElseThrow(() -> new ResourceNotFoundException("Professional record not found for current user"));
    }

    private com.palavecino.backend.availability.DayOfWeek toDayOfWeek(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> com.palavecino.backend.availability.DayOfWeek.MONDAY;
            case TUESDAY -> com.palavecino.backend.availability.DayOfWeek.TUESDAY;
            case WEDNESDAY -> com.palavecino.backend.availability.DayOfWeek.WEDNESDAY;
            case THURSDAY -> com.palavecino.backend.availability.DayOfWeek.THURSDAY;
            case FRIDAY -> com.palavecino.backend.availability.DayOfWeek.FRIDAY;
            case SATURDAY -> com.palavecino.backend.availability.DayOfWeek.SATURDAY;
            case SUNDAY -> com.palavecino.backend.availability.DayOfWeek.SUNDAY;
        };
    }

    /**
     * Recurring blocks (e.g. the EMSELLA machine, a professional's fixed weekly slot) permanently
     * occupy one of the clinic's boxes. Each overlapping active block reduces the general capacity
     * pool by one - except when the requested service matches a block's own service: that
     * appointment uses the block's dedicated box instead of competing for general capacity, so it
     * only needs to check that no other appointment is already occupying that specific box.
     */
    private CapacityCheckResult checkCapacity(com.palavecino.backend.availability.DayOfWeek dayOfWeek,
                                               Service service, LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        List<RecurringBlock> overlappingBlocks = recurringBlockRepository.findActiveOverlapping(
                dayOfWeek, rangeStart.toLocalTime(), rangeEnd.toLocalTime());

        boolean matchesBlockService = overlappingBlocks.stream()
                .anyMatch(block -> block.getService() != null && block.getService().getId().equals(service.getId()));

        if (matchesBlockService) {
            boolean boxTaken = !appointmentRepository
                    .findOverlappingActiveByService(service, rangeStart, rangeEnd)
                    .isEmpty();
            return new CapacityCheckResult(boxTaken, true);
        }

        List<Long> blockServiceIds = overlappingBlocks.stream()
                .map(RecurringBlock::getService)
                .filter(Objects::nonNull)
                .map(Service::getId)
                .distinct()
                .toList();

        long generalCount = blockServiceIds.isEmpty()
                ? appointmentRepository.countOverlappingActive(rangeStart, rangeEnd)
                : appointmentRepository.countOverlappingActiveExcludingServices(rangeStart, rangeEnd, blockServiceIds);

        int effectiveCapacity = maxConcurrentAppointments - overlappingBlocks.size();
        return new CapacityCheckResult(generalCount >= effectiveCapacity, false);
    }

    private record CapacityCheckResult(boolean full, boolean reservedBlockBox) {
    }
}
