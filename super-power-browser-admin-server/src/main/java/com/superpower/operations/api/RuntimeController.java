package com.superpower.operations.api;

import com.superpower.operations.config.BrowserConfiguration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class RuntimeController {
    private final BrowserConfiguration configs;
    public RuntimeController(BrowserConfiguration configs) { this.configs=configs; }
    @GetMapping("/api/runtime/config") public ResponseEntity<?> config() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(configs.runtime());
    }
}
