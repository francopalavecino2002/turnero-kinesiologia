package com.palavecino.backend.appointment;

import com.palavecino.backend.appointment.dto.AppointmentMapper;
import com.palavecino.backend.appointment.dto.AppointmentResponse;
import com.palavecino.backend.appointment.dto.AvailableSlotResponse;
import com.palavecino.backend.appointment.dto.CreateAppointmentRequest;
import com.palavecino.backend.availability.Availability;
import com.palavecino.backend.availability.AvailabilityRepository;
import com.palavecino.backend.exception.BusinessRuleViolationException;
import com.palavecino.backend.exception.ConflictException;
import com.palavecino.backend.exception.ResourceNotFoundException;
import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.patient.PatientRepository;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.service.Service;
import com.palavecino.backend.service.ServiceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final ProfessionalRepository professionalRepository;
    private final PatientRepository patientRepository;
    private final ServiceRepository serviceRepository;
    private final AvailabilityRepository availabilityRepository;
    private final Clock clock;
    private final int maxConcurrentAppointments;

    public AppointmentService(AppointmentRepository appointmentRepository,
                               ProfessionalRepository professionalRepository,
                               PatientRepository patientRepository,
                               ServiceRepository serviceRepository,
                               AvailabilityRepository availabilityRepository,
                               Clock clock,
                               @Value("${clinic.max-concurrent-appointments}") int maxConcurrentAppointments) {
        this.appointmentRepository = appointmentRepository;
        this.professionalRepository = professionalRepository;
        this.patientRepository = patientRepository;
        this.serviceRepository = serviceRepository;
        this.availabilityRepository = availabilityRepository;
        this.clock = clock;
        this.maxConcurrentAppointments = maxConcurrentAppointments;
    }

    public List<AppointmentResponse> findByProfessionalAndDate(Long professionalId, LocalDate date) {
        Professional professional = professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Professional not found with id " + professionalId));

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        return appointmentRepository.findByProfessionalAndDate(professional, dayStart, dayEnd).stream()
                .map(AppointmentMapper::toResponse)
                .toList();
    }

    public List<AppointmentResponse> findByPatient(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient not found with id " + patientId));

        return appointmentRepository.findByPatientWithDetails(patient).stream()
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
                boolean capacityFull = appointmentRepository
                        .countOverlappingActive(rangeStart, rangeEnd) >= maxConcurrentAppointments;

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

    public AppointmentResponse findById(Long id) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id " + id));
        return AppointmentMapper.toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse bookAppointment(CreateAppointmentRequest request) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient not found with id " + request.patientId()));

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

        boolean capacityFull = appointmentRepository.countOverlappingActive(dateTime, rangeEnd)
                >= maxConcurrentAppointments;
        if (capacityFull) {
            throw new ConflictException("No capacity available at " + dateTime);
        }

        Appointment appointment = new Appointment(patient, professional, service, dateTime, AppointmentStatus.BOOKED);
        appointment = appointmentRepository.save(appointment);

        return AppointmentMapper.toResponse(appointmentRepository.findByIdWithDetails(appointment.getId())
                .orElseThrow());
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
}
