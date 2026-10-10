package com.tanmaysinghx.portalsso.application.web;

import com.tanmaysinghx.portalsso.application.service.AccessRequestService;
import com.tanmaysinghx.portalsso.application.web.dto.AccessRequestResponse;
import com.tanmaysinghx.portalsso.application.web.dto.AdminSummaryResponse;
import com.tanmaysinghx.portalsso.application.web.dto.SubmitAccessRequestRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/access-requests")
@PreAuthorize("isAuthenticated()")
public class UserAccessRequestController {

    private final AccessRequestService accessRequestService;

    public UserAccessRequestController(AccessRequestService accessRequestService) {
        this.accessRequestService = accessRequestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccessRequestResponse submit(@Valid @RequestBody SubmitAccessRequestRequest request,
                                         Authentication authentication) {
        return accessRequestService.submitRequest(authentication.getName(), request);
    }

    @GetMapping
    public List<AccessRequestResponse> listMyRequests(Authentication authentication) {
        return accessRequestService.listForUser(authentication.getName());
    }

    @GetMapping("/admins")
    public List<AdminSummaryResponse> listAdmins() {
        return accessRequestService.listAdmins();
    }
}
