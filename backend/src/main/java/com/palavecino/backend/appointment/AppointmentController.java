package com.palavecino.backend.appointment;

import com.palavecino.backend.appointment.dto.AppointmentResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping(params = {"professionalId", "date"})
    public ResponseEntity<List<AppointmentResponse>> getByProfessionalAndDate(
            @RequestParam Long professionalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(appointmentService.findByProfessionalAndDate(professionalId, date));
    }

    @GetMapping(params = "patientId")
    public ResponseEntity<List<AppointmentResponse>> getByPatient(@RequestParam Long patientId) {
        return ResponseEntity.ok(appointmentService.findByPatient(patientId));
    }

    @GetMapping(params = "date")
    public ResponseEntity<List<AppointmentResponse>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(appointmentService.findAllByDate(date));
    }
}
