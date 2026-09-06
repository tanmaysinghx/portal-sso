package com.tanmaysinghx.portalsso.setup.web;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/download")
public class DownloadController {

    private static final String GITHUB_RELEASE_JAR_URL =
            "https://github.com/tanmaysinghx/portal-sso/releases/latest/download/portal-sso.jar";

    @GetMapping({"", "/portal-sso.jar"})
    public ResponseEntity<?> downloadLatestJar() {
        Path[] candidates = new Path[] {
            Path.of("portal-sso.jar"),
            Path.of("app.jar"),
            Path.of("/app/app.jar"),
            Path.of("target/portal-sso.jar"),
            Path.of("target/portal-server-0.0.1-SNAPSHOT.jar")
        };

        for (Path candidate : candidates) {
            if (Files.exists(candidate) && Files.isReadable(candidate)) {
                Resource resource = new FileSystemResource(candidate);
                try {
                    long length = Files.size(candidate);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"portal-sso.jar\"")
                            .contentLength(length)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .body(resource);
                } catch (Exception ignored) {
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"portal-sso.jar\"")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .body(resource);
                }
            }
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(GITHUB_RELEASE_JAR_URL))
                .build();
    }
}
