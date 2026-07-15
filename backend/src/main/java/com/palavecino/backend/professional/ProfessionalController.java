package com.palavecino.backend.professional;

import com.palavecino.backend.exception.ResourceNotFoundException;
import com.palavecino.backend.professional.dto.ProfessionalMapper;
import com.palavecino.backend.professional.dto.ProfessionalResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only professional lookup, public for the same reason as ServiceController: a prospective
 * patient needs to see "you'll be seen by X" before (or without) signing up.
 */
@RestController
@RequestMapping("/api/professionals")
public class ProfessionalController {

    private final ProfessionalRepository professionalRepository;

    public ProfessionalController(ProfessionalRepository professionalRepository) {
        this.professionalRepository = professionalRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfessionalResponse> getById(@PathVariable Long id) {
        Professional professional = professionalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Professional not found with id " + id));
        return ResponseEntity.ok(ProfessionalMapper.toResponse(professional));
    }
}
