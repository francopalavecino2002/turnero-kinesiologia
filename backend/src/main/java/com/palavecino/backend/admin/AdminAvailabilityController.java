package com.palavecino.backend.admin;

import com.palavecino.backend.availability.dto.AvailabilityResponse;
import com.palavecino.backend.availability.dto.CreateAvailabilityRequest;
import com.palavecino.backend.availability.dto.UpdateAvailabilityRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/availability")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAvailabilityController {

    private final AdminAvailabilityService adminAvailabilityService;

    public AdminAvailabilityController(AdminAvailabilityService adminAvailabilityService) {
        this.adminAvailabilityService = adminAvailabilityService;
    }

    @GetMapping
    public ResponseEntity<List<AvailabilityResponse>> list(
            @RequestParam Long professionalId) {
        return ResponseEntity.ok(adminAvailabilityService.list(professionalId));
    }

    @PostMapping
    public ResponseEntity<AvailabilityResponse> create(@Valid @RequestBody CreateAvailabilityRequest request) {
        AvailabilityResponse response = adminAvailabilityService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/availability/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AvailabilityResponse> update(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateAvailabilityRequest request) {
        return ResponseEntity.ok(adminAvailabilityService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminAvailabilityService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
