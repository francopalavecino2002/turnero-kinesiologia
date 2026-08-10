package com.palavecino.backend.patient;

import com.palavecino.backend.patient.dto.PatientSearchResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff-only patient lookup, used by the manual booking flow to find an existing patient
 * (registered or guest) by name or email instead of re-entering their data.
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PROFESSIONAL', 'ADMIN')")
    public ResponseEntity<List<PatientSearchResponse>> search(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(patientService.search(search));
    }
}
