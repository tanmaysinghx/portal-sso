package com.tanmaysinghx.portalsso.user.web;

import com.tanmaysinghx.portalsso.user.web.dto.UserSessionDto;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/user/sessions")
public class UserSessionController {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public UserSessionController(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @GetMapping
    public List<UserSessionDto> getMySessions(Principal principal, HttpServletRequest request) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(principal.getName());
        String currentSessionId = request.getSession(false) != null ? request.getSession(false).getId() : null;

        return sessions.values().stream()
                .map(session -> new UserSessionDto(
                        session.getId(),
                        session.getCreationTime(),
                        session.getLastAccessedTime(),
                        session.getAttribute("ipAddress") != null ? session.getAttribute("ipAddress") : "Unknown",
                        session.getAttribute("userAgent") != null ? session.getAttribute("userAgent") : "Unknown Device",
                        session.getId().equals(currentSessionId)
                ))
                .sorted(Comparator.comparing(UserSessionDto::lastAccessedTime).reversed())
                .toList();
    }

    @DeleteMapping("/{sessionId}")
    public void revokeMySession(@PathVariable String sessionId, Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        // Must verify the session actually belongs to this user before deleting it
        Map<String, ? extends Session> userSessions = sessionRepository.findByPrincipalName(principal.getName());
        
        if (!userSessions.containsKey(sessionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        sessionRepository.deleteById(sessionId);
    }
}
