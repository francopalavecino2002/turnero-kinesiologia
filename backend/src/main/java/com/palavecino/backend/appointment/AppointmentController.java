package com.palavecino.backend.appointment;

import com.palavecino.backend.appointment.dto.AppointmentResponse;
import com.palavecino.backend.appointment.dto.AvailableSlotResponse;
import com.palavecino.backend.appointment.dto.CreateAppointmentRequest;
import com.palavecino.backend.security.AuthenticatedUserResolver;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public AppointmentController(AppointmentService appointmentService,
                                  AuthenticatedUserResolver authenticatedUserResolver) {
        this.appointmentService = appointmentService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> getById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(appointmentService.findById(id, authenticatedUserResolver.resolve(authentication)));
    }

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> bookAppointment(@Valid @RequestBody CreateAppointmentRequest request,
                                                                 Authentication authentication) {
        AppointmentResponse response = appointmentService.bookAppointment(
                request, authenticatedUserResolver.resolve(authentication));
        return ResponseEntity.created(URI.create("/api/appointments/" + response.id())).body(response);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<AppointmentResponse>> getMyAppointments(Authentication authentication) {
        return ResponseEntity.ok(appointmentService.findMyAppointments(authenticatedUserResolver.resolve(authentication)));
    }

    @GetMapping("/my-agenda")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public ResponseEntity<List<AppointmentResponse>> getMyAgenda(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        return ResponseEntity.ok(appointmentService.findMyAgenda(date, authenticatedUserResolver.resolve(authentication)));
    }

    @GetMapping(params = {"professionalId", "date"})
    @PreAuthorize("hasAnyRole('PROFESSIONAL', 'ADMIN')")
    public ResponseEntity<List<AppointmentResponse>> getByProfessionalAndDate(
            @RequestParam Long professionalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        return ResponseEntity.ok(appointmentService.findByProfessionalAndDate(
                professionalId, date, authenticatedUserResolver.resolve(authentication)));
    }

    @GetMapping(params = "date")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AppointmentResponse>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(appointmentService.findAllByDate(date));
    }

    @GetMapping("/available-slots")
    public ResponseEntity<List<AvailableSlotResponse>> getAvailableSlots(
            @RequestParam Long professionalId,
            @RequestParam Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(appointmentService.findAvailableSlots(professionalId, serviceId, date));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<AppointmentResponse> cancel(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(appointmentService.cancel(id, authenticatedUserResolver.resolve(authentication)));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('PROFESSIONAL', 'ADMIN')")
    public ResponseEntity<AppointmentResponse> confirm(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(appointmentService.confirm(id, authenticatedUserResolver.resolve(authentication)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('PROFESSIONAL', 'ADMIN')")
    public ResponseEntity<AppointmentResponse> complete(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(appointmentService.complete(id, authenticatedUserResolver.resolve(authentication)));
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasAnyRole('PROFESSIONAL', 'ADMIN')")
    public ResponseEntity<AppointmentResponse> noShow(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(appointmentService.noShow(id, authenticatedUserResolver.resolve(authentication)));
    }
}
