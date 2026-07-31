package com.palavecino.backend.admin;

import com.palavecino.backend.recurringblock.dto.RecurringBlockAdminResponse;
import com.palavecino.backend.recurringblock.dto.RecurringBlockCreateRequest;
import com.palavecino.backend.recurringblock.dto.RecurringBlockUpdateRequest;
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
@RequestMapping("/api/admin/recurring-blocks")
public class AdminRecurringBlockController {

    private final AdminRecurringBlockService adminRecurringBlockService;

    public AdminRecurringBlockController(AdminRecurringBlockService adminRecurringBlockService) {
        this.adminRecurringBlockService = adminRecurringBlockService;
    }

    @GetMapping
    public ResponseEntity<List<RecurringBlockAdminResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(adminRecurringBlockService.list(includeInactive));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecurringBlockAdminResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(adminRecurringBlockService.findById(id));
    }

    @PostMapping
    public ResponseEntity<RecurringBlockAdminResponse> create(@Valid @RequestBody RecurringBlockCreateRequest request) {
        RecurringBlockAdminResponse response = adminRecurringBlockService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/recurring-blocks/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringBlockAdminResponse> update(@PathVariable Long id,
                                                                @Valid @RequestBody RecurringBlockUpdateRequest request) {
        return ResponseEntity.ok(adminRecurringBlockService.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<RecurringBlockAdminResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(adminRecurringBlockService.deactivate(id));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<RecurringBlockAdminResponse> reactivate(@PathVariable Long id) {
        return ResponseEntity.ok(adminRecurringBlockService.reactivate(id));
    }
}
