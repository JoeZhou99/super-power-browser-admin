package com.superpower.operations.api;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
    @GetMapping("/api/health") public Object health() { return Map.of("status","ok","service","operations"); }
}
