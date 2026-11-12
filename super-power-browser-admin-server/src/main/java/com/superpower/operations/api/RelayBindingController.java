package com.superpower.operations.api;

import com.superpower.operations.config.ConfigOperator;
import com.superpower.operations.relay.RelayBindings;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class RelayBindingController {
    private final RelayBindings bindings;
    public RelayBindingController(RelayBindings bindings){this.bindings=bindings;}
    private ResponseEntity<?> result(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping("/customer-proxies/{id}/relays") public ResponseEntity<?> get(@AuthenticationPrincipal ConfigOperator a,@PathVariable String id){return result(bindings.get(a,id));}
    @PutMapping("/customer-proxies/{id}/relays") public ResponseEntity<?> put(@AuthenticationPrincipal ConfigOperator a,@PathVariable String id,@RequestBody Map<String,Object> b){return result(bindings.put(a,id,b));}
    @PostMapping("/customer-proxies/relays/batch") public ResponseEntity<?> batch(@AuthenticationPrincipal ConfigOperator a,@RequestBody Map<String,Object> b){return result(bindings.batch(a,b));}
    @PostMapping("/customer-proxies/relays/allocate") public ResponseEntity<?> allocate(@AuthenticationPrincipal ConfigOperator a,@RequestBody Map<String,Object> b){return result(bindings.allocate(a,b));}
    @GetMapping("/relay-nodes/{id}/bindings") public ResponseEntity<?> node(@AuthenticationPrincipal ConfigOperator a,@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return result(bindings.nodeBindings(a,id,page,size));}
}
