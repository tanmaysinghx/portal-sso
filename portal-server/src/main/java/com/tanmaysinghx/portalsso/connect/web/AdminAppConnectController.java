package com.tanmaysinghx.portalsso.connect.web;

import com.tanmaysinghx.portalsso.connect.service.AppConnectService;
import com.tanmaysinghx.portalsso.connect.web.dto.ConnectRequestSummary;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pending connection requests, so the console can point administrators at them. */
@RestController
@RequestMapping("/api/admin/connect-requests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAppConnectController {

    private final AppConnectService connectService;

    public AdminAppConnectController(AppConnectService connectService) {
        this.connectService = connectService;
    }

    @GetMapping
    public List<ConnectRequestSummary> pending() {
        return connectService.listPending();
    }
}
