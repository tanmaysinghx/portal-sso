package com.tanmaysinghx.portalsso.database.web;

import com.tanmaysinghx.portalsso.database.dto.DatabaseStatusResponse;
import com.tanmaysinghx.portalsso.database.dto.MigrateDatabaseRequest;
import com.tanmaysinghx.portalsso.database.dto.MigrateDatabaseResponse;
import com.tanmaysinghx.portalsso.database.dto.TestConnectionRequest;
import com.tanmaysinghx.portalsso.database.dto.TestConnectionResponse;
import com.tanmaysinghx.portalsso.database.service.DatabaseMigrationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/database")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDatabaseController {

    private final DatabaseMigrationService migrationService;

    public AdminDatabaseController(DatabaseMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @GetMapping("/status")
    public ResponseEntity<DatabaseStatusResponse> getStatus() {
        return ResponseEntity.ok(migrationService.getDatabaseStatus());
    }

    @PostMapping("/test-connection")
    public ResponseEntity<TestConnectionResponse> testConnection(@Valid @RequestBody TestConnectionRequest request) {
        return ResponseEntity.ok(migrationService.testConnection(request));
    }

    @PostMapping("/migrate")
    public ResponseEntity<MigrateDatabaseResponse> migrate(@Valid @RequestBody MigrateDatabaseRequest request) {
        return ResponseEntity.ok(migrationService.migrate(request));
    }

    @PostMapping("/restart")
    public ResponseEntity<java.util.Map<String, Object>> restart() {
        migrationService.restartApplication();
        return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", "Application restart initiated. The server will reload momentarily."));
    }
}
