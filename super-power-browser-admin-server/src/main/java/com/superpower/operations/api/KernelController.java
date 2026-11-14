package com.superpower.operations.api;

import com.superpower.operations.config.ConfigOperator;
import com.superpower.operations.runtime.KernelReleases;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/kernels")
public class KernelController {
    private final KernelReleases releases;
    public KernelController(KernelReleases releases) { this.releases = releases; }

    @GetMapping public Object list(@AuthenticationPrincipal ConfigOperator operator) { return Map.of("items", releases.list(operator)); }
    @PostMapping public Object create(@AuthenticationPrincipal ConfigOperator operator, @RequestBody Map<String, Object> body) { return releases.create(operator, body); }
    @PostMapping("/{id}/upload")
    public ResponseEntity<Object> upload(@AuthenticationPrincipal ConfigOperator operator, @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(releases.upload(operator, id));
    }
    @GetMapping("/{id}/download")
    public ResponseEntity<Object> download(@AuthenticationPrincipal ConfigOperator operator, @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(releases.download(operator, id));
    }
    @PostMapping("/{id}/publish") public Object publish(@AuthenticationPrincipal ConfigOperator operator, @PathVariable String id, @RequestBody Map<String, Object> body) { return releases.publish(operator, id, body); }
    @PostMapping("/{id}/recommend") public Object recommend(@AuthenticationPrincipal ConfigOperator operator, @PathVariable String id) { return releases.recommend(operator, id); }
    @PostMapping("/{id}/disable") public Object disable(@AuthenticationPrincipal ConfigOperator operator, @PathVariable String id) { return releases.disable(operator, id); }
}
