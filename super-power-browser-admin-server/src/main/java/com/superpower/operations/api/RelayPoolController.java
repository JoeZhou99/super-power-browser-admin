package com.superpower.operations.api;

import com.superpower.operations.config.ConfigOperator;
import com.superpower.operations.relay.RelayPools;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class RelayPoolController {
    private final RelayPools pools;
    public RelayPoolController(RelayPools pools) { this.pools=pools; }
    @GetMapping("/relay-pools") public Object list(@AuthenticationPrincipal ConfigOperator a) { return pools.list(a); }
    @GetMapping("/relay-nodes") public Object allNodes(@AuthenticationPrincipal ConfigOperator a,@RequestParam Map<String,String> query) { return pools.allNodes(a,query); }
    @GetMapping("/relay-pools/{id}") public Object get(@AuthenticationPrincipal ConfigOperator a,@PathVariable String id) { return pools.get(a,id); }
    @PostMapping("/relay-pools") public Object create(@AuthenticationPrincipal ConfigOperator a,@RequestBody Map<String,Object> b) { return pools.create(a,b); }
    @PutMapping("/relay-pools/{id}") public Object update(@AuthenticationPrincipal ConfigOperator a,@PathVariable String id,@RequestBody Map<String,Object> b) { return pools.update(a,id,b); }
    @GetMapping("/relay-pools/{id}/nodes") public Object nodes(@AuthenticationPrincipal ConfigOperator a,@PathVariable String id) { return pools.nodes(a,id); }
    @PostMapping("/relay-pools/{id}/nodes") public Object addNode(@AuthenticationPrincipal ConfigOperator a,@PathVariable String id,@RequestBody Map<String,Object> b) { return pools.saveNode(a,id,null,b); }
    @PutMapping("/relay-pools/{id}/nodes/{nodeId}") public Object updateNode(@AuthenticationPrincipal ConfigOperator a,@PathVariable String id,@PathVariable String nodeId,@RequestBody Map<String,Object> b) { return pools.saveNode(a,id,nodeId,b); }
    @GetMapping("/relay-pools/{id}/grants") public Object grants(@AuthenticationPrincipal ConfigOperator a,@PathVariable String id) { return pools.grants(a,id); }
    @PutMapping("/relay-pools/{id}/grants") public Object saveGrants(@AuthenticationPrincipal ConfigOperator a,@PathVariable String id,@RequestBody Map<String,Object> b) { return pools.saveGrants(a,id,b); }
    @GetMapping("/relay-tenants") public Object tenants(@AuthenticationPrincipal ConfigOperator a,@RequestParam(defaultValue="") String query) { return pools.tenants(a,query); }
}
