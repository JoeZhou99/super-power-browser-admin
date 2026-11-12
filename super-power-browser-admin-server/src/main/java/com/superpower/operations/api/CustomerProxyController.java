package com.superpower.operations.api;

import com.superpower.operations.config.ConfigOperator;
import com.superpower.operations.proxy.CustomerProxies;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Inspection only; mutations remain owned by the customer's business service. */
@RestController
@RequestMapping("/api/admin/customer-proxies")
public class CustomerProxyController {
    private final CustomerProxies proxies;
    public CustomerProxyController(CustomerProxies proxies) { this.proxies=proxies; }
    @GetMapping public ResponseEntity<?> list(@AuthenticationPrincipal ConfigOperator actor,@RequestParam Map<String,String> query) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(proxies.list(actor,query));
    }
    @GetMapping("/{id}") public ResponseEntity<?> get(@AuthenticationPrincipal ConfigOperator actor,@PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(proxies.get(actor,id));
    }
}
