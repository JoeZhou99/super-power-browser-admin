package com.superpower.operations.api;

import com.superpower.operations.config.*;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class ConfigurationController {
    private final BrowserConfiguration configs;
    private final ConfigOperatorAuth operators;
    private final PublicAuthRateLimiter loginLimits=new PublicAuthRateLimiter();
    public ConfigurationController(BrowserConfiguration configs,ConfigOperatorAuth operators) { this.configs=configs;this.operators=operators; }
    @GetMapping("/health") public Object health() { return Map.of("status","ok","service","operations"); }
    @PostMapping("/auth/login") public Object login(@RequestBody Map<String,Object> body,jakarta.servlet.http.HttpServletRequest request) {
        loginLimits.check("ops-login",request.getRemoteAddr(),10);return operators.login(body);
    }
    @GetMapping("/auth/me") public ConfigOperator me(@AuthenticationPrincipal ConfigOperator operator) { return operator; }
    @PostMapping("/auth/logout") public void logout(@RequestHeader("Authorization") String header) { operators.logout(header.substring(7)); }
    @GetMapping("/configs") public Object configs(@AuthenticationPrincipal ConfigOperator operator) { return configs.list(operator); }
    @GetMapping("/configs/{key}") public Object config(@AuthenticationPrincipal ConfigOperator operator,@PathVariable String key) { return configs.get(operator,key); }
    @PutMapping("/configs/{key}") public Object save(@AuthenticationPrincipal ConfigOperator operator,@PathVariable String key,@RequestBody Map<String,Object> body) { return configs.save(operator,key,body); }
    @GetMapping("/config-audit") public Object audit(@AuthenticationPrincipal ConfigOperator operator,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return configs.audit(operator,page,size); }
}
