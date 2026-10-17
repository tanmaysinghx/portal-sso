package com.tanmaysinghx.portalsso.connect.web;

import com.tanmaysinghx.portalsso.common.web.ClientIpResolver;
import com.tanmaysinghx.portalsso.connect.service.AppConnectService;
import com.tanmaysinghx.portalsso.connect.web.dto.ConnectRedeemRequest;
import com.tanmaysinghx.portalsso.connect.web.dto.ConnectRedeemResponse;
import com.tanmaysinghx.portalsso.connect.web.dto.ConnectRequestCreatedResponse;
import com.tanmaysinghx.portalsso.connect.web.dto.ConnectRequestStatusResponse;
import com.tanmaysinghx.portalsso.connect.web.dto.ConnectRequestSubmission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-to-server half of "Connect with Portal SSO", called by the app's backend. Unauthenticated
 * by design (the app has no credentials yet): submitting only creates a request an administrator
 * must approve, and redeeming needs both the delivery code and the verifier. Rate limited per IP.
 */
@RestController
@RequestMapping("/api/external/connect")
public class ExternalAppConnectController {

    private final AppConnectService connectService;

    public ExternalAppConnectController(AppConnectService connectService) {
        this.connectService = connectService;
    }

    @PostMapping("/requests")
    public ResponseEntity<ConnectRequestCreatedResponse> submit(
            @Valid @RequestBody ConnectRequestSubmission submission, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(connectService.submit(submission, ClientIpResolver.getClientIp(request)));
    }

    @GetMapping("/requests/{id}")
    public ConnectRequestStatusResponse status(@PathVariable UUID id) {
        return connectService.status(id);
    }

    /** The only response that ever carries this app's secrets; never cached anywhere. */
    @PostMapping("/requests/{id}/redeem")
    public ResponseEntity<ConnectRedeemResponse> redeem(@PathVariable UUID id, @Valid @RequestBody ConnectRedeemRequest body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(connectService.redeem(id, body.code(), body.codeVerifier()));
    }
}
