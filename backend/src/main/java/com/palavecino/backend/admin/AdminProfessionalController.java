package com.palavecino.backend.admin;

import com.palavecino.backend.professional.dto.ProfessionalAdminResponse;
import com.palavecino.backend.professional.dto.ProfessionalCreateRequest;
import com.palavecino.backend.professional.dto.ProfessionalCreatedResponse;
import com.palavecino.backend.professional.dto.ProfessionalUpdateRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/professionals")
public class AdminProfessionalController {

    private final AdminProfessionalService adminProfessionalService;

    public AdminProfessionalController(AdminProfessionalService adminProfessionalService) {
        this.adminProfessionalService = adminProfessionalService;
    }

    @GetMapping
    public ResponseEntity<List<ProfessionalAdminResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(adminProfessionalService.list(includeInactive));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfessionalAdminResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(adminProfessionalService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ProfessionalCreatedResponse> create(@Valid @RequestBody ProfessionalCreateRequest request) {
        ProfessionalCreatedResponse response = adminProfessionalService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/professionals/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfessionalAdminResponse> update(@PathVariable Long id,
                                                             @Valid @RequestBody ProfessionalUpdateRequest request) {
        return ResponseEntity.ok(adminProfessionalService.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ProfessionalAdminResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(adminProfessionalService.deactivate(id));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<ProfessionalAdminResponse> reactivate(@PathVariable Long id) {
        return ResponseEntity.ok(adminProfessionalService.reactivate(id));
    }
}
