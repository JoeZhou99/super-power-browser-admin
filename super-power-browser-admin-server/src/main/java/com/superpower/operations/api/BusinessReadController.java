package com.superpower.operations.api;

import com.superpower.operations.business.BusinessReadModels;
import com.superpower.operations.config.ConfigOperator;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** First read-only migration slice for business data used by the new admin. */
@RestController
@RequestMapping("/api/admin")
public class BusinessReadController {
    private final BusinessReadModels models;
    public BusinessReadController(BusinessReadModels models) { this.models = models; }
    @GetMapping("/members") public ResponseEntity<?> members(@AuthenticationPrincipal ConfigOperator actor, @RequestParam Map<String, String> query) { return noStore(models.members(actor, query)); }
    @GetMapping("/orders") public ResponseEntity<?> orders(@AuthenticationPrincipal ConfigOperator actor, @RequestParam Map<String, String> query) { return noStore(models.orders(actor, query)); }
    @GetMapping("/skills") public ResponseEntity<?> skills(@AuthenticationPrincipal ConfigOperator actor, @RequestParam Map<String, String> query) { return noStore(models.skills(actor, query)); }
    private static ResponseEntity<?> noStore(Object body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
