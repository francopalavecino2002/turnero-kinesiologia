package com.palavecino.backend.professional;

import com.palavecino.backend.exception.ResourceNotFoundException;
import com.palavecino.backend.professional.dto.ProfessionalMapper;
import com.palavecino.backend.professional.dto.ProfessionalResponse;
import com.palavecino.backend.security.AuthenticatedUserResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public ProfessionalController(ProfessionalRepository professionalRepository,
                                   AuthenticatedUserResolver authenticatedUserResolver) {
        this.professionalRepository = professionalRepository;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    /**
     * Lets the frontend resolve "myself" for a logged-in professional without exposing
     * professionalId in the JWT - e.g. the manual-booking dialog needs it to implicitly book on
     * their own agenda without asking them to pick themselves from a list.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public ResponseEntity<ProfessionalResponse> getMe(Authentication authentication) {
        Long professionalId = authenticatedUserResolver.resolve(authentication).professionalId();
        Professional professional = professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Professional record not found for current user"));
        return ResponseEntity.ok(ProfessionalMapper.toResponse(professional));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfessionalResponse> getById(@PathVariable Long id) {
        Professional professional = professionalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Professional not found with id " + id));
        // A deactivated professional is invisible to the public: treat it like it doesn't exist
        // so patients never see (or end up choosing) a professional they can't actually book.
        if (!professional.getUser().isActive()) {
            throw new ResourceNotFoundException("Professional not found with id " + id);
        }
        return ResponseEntity.ok(ProfessionalMapper.toResponse(professional));
    }
}
