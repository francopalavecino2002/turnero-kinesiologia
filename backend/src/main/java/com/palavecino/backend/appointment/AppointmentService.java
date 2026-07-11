package com.palavecino.backend.appointment;

import com.palavecino.backend.appointment.dto.AppointmentMapper;
import com.palavecino.backend.appointment.dto.AppointmentResponse;
import com.palavecino.backend.exception.ResourceNotFoundException;
import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.patient.PatientRepository;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final ProfessionalRepository professionalRepository;
    private final PatientRepository patientRepository;

    public AppointmentService(AppointmentRepository appointmentRepository,
                               ProfessionalRepository professionalRepository,
                               PatientRepository patientRepository) {
        this.appointmentRepository = appointmentRepository;
        this.professionalRepository = professionalRepository;
        this.patientRepository = patientRepository;
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
}
