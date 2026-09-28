package com.tanmaysinghx.portalsso.user.web;

import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.web.dto.UserSessionDto;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
@RequestMapping("/api/admin/users/{userId}/sessions")
public class AdminSessionController {

    private final UserRepository userRepository;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public AdminSessionController(
            UserRepository userRepository,
            FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
    }

    @GetMapping
    public List<UserSessionDto> getSessions(
            @PathVariable UUID userId,
            HttpServletRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(user.getEmail());
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
    public void revokeSession(
            @PathVariable UUID userId,
            @PathVariable String sessionId) {
        // Technically we just need the sessionId to delete, but verifying the user exists is good practice
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        sessionRepository.deleteById(sessionId);
    }
}
