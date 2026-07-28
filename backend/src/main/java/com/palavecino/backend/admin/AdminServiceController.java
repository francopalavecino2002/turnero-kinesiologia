package com.palavecino.backend.admin;

import com.palavecino.backend.service.ServiceService;
import com.palavecino.backend.service.dto.ServiceAdminResponse;
import com.palavecino.backend.service.dto.ServiceCreateRequest;
import com.palavecino.backend.service.dto.ServiceUpdateRequest;
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
@RequestMapping("/api/admin/services")
public class AdminServiceController {

    private final ServiceService serviceService;

    public AdminServiceController(ServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @GetMapping
    public ResponseEntity<List<ServiceAdminResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(serviceService.list(includeInactive));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceAdminResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(serviceService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ServiceAdminResponse> create(@Valid @RequestBody ServiceCreateRequest request) {
        ServiceAdminResponse response = serviceService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/services/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceAdminResponse> update(@PathVariable Long id,
                                                        @Valid @RequestBody ServiceUpdateRequest request) {
        return ResponseEntity.ok(serviceService.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ServiceAdminResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(serviceService.deactivate(id));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<ServiceAdminResponse> reactivate(@PathVariable Long id) {
        return ResponseEntity.ok(serviceService.reactivate(id));
    }
}
