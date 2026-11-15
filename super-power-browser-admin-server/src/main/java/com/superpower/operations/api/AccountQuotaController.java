package com.superpower.operations.api;

import com.superpower.operations.business.AccountQuotas;
import com.superpower.operations.config.ConfigOperator;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AccountQuotaController {
    private final AccountQuotas quotas;
    public AccountQuotaController(AccountQuotas quotas) { this.quotas = quotas; }
    @GetMapping("/account-quotas")
    public ResponseEntity<?> list(@AuthenticationPrincipal ConfigOperator actor, @RequestParam Map<String, String> query) {
        return noStore(quotas.list(actor, query));
    }
    @PutMapping("/account-quotas/{userId}")
    public ResponseEntity<?> update(@AuthenticationPrincipal ConfigOperator actor, @PathVariable String userId, @RequestBody Map<String, Object> body) {
        return noStore(quotas.update(actor, userId, body));
    }
    @GetMapping("/account-quotas/{userId}/audit")
    public ResponseEntity<?> audit(@AuthenticationPrincipal ConfigOperator actor, @PathVariable String userId, @RequestParam Map<String, String> query) {
        return noStore(quotas.audit(actor, userId, query));
    }
    private static ResponseEntity<?> noStore(Object body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
