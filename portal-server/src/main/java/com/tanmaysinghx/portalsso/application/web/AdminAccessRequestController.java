package com.tanmaysinghx.portalsso.application.web;

import com.tanmaysinghx.portalsso.application.entity.AccessRequestStatus;
import com.tanmaysinghx.portalsso.application.service.AccessRequestService;
import com.tanmaysinghx.portalsso.application.web.dto.AccessRequestResponse;
import com.tanmaysinghx.portalsso.application.web.dto.ReviewAccessRequestRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/access-requests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccessRequestController {

    private final AccessRequestService accessRequestService;

    public AdminAccessRequestController(AccessRequestService accessRequestService) {
        this.accessRequestService = accessRequestService;
    }

    @GetMapping
    public List<AccessRequestResponse> list(@RequestParam(required = false) AccessRequestStatus status) {
        return accessRequestService.listAll(status);
    }

    @GetMapping("/count")
    public Map<String, Long> countPending() {
        return Map.of("pending", accessRequestService.countPending());
    }

    @PostMapping("/{id}/approve")
    public AccessRequestResponse approve(@PathVariable UUID id,
                                          @Valid @RequestBody(required = false) ReviewAccessRequestRequest request,
                                          Authentication authentication) {
        String comment = request != null ? request.comment() : null;
        return accessRequestService.approve(id, authentication.getName(), comment);
    }

    @PostMapping("/{id}/deny")
    public AccessRequestResponse deny(@PathVariable UUID id,
                                       @Valid @RequestBody(required = false) ReviewAccessRequestRequest request,
                                       Authentication authentication) {
        String comment = request != null ? request.comment() : null;
        return accessRequestService.deny(id, authentication.getName(), comment);
    }
}
