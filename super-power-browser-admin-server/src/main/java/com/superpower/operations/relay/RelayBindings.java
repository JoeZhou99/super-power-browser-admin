package com.superpower.operations.relay;

import com.superpower.operations.config.*;
import com.superpower.operations.shared.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.superpower.operations.shared.Data.*;
import static com.superpower.operations.shared.BusinessException.require;

/** Only operations can bind platform nodes to a customer's own fixed exit proxy. */
@Service
public class RelayBindings {
    private final Db db;
    private final ConfigOperatorAuth operators;
    private static final Set<String> PROTOCOLS=Set.of("http","https","socks5","ss","vmess","vless");
    private static final String VALID="p.enabled=TRUE AND n.enabled=TRUE AND g.enabled=TRUE AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP)";
    private static final String NODE="n.id,n.pool_id,p.name AS pool_name,n.name,n.region,n.protocol,n.host,n.port,n.enabled,p.enabled AS pool_enabled,n.sort_order,"
        +"CASE WHEN g.enabled=TRUE AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP) THEN TRUE ELSE FALSE END AS authorized,"
        +"CASE WHEN "+VALID+" THEN TRUE ELSE FALSE END AS available,"
        +"(SELECT COUNT(*) FROM device_relay_node counted WHERE counted.node_id=n.id) AS bound_proxy_count";
    public RelayBindings(Db db,ConfigOperatorAuth operators){this.db=db;this.operators=operators;}

    private Map<String,Object> device(String id,boolean lock) {
        require(id!=null&&!id.isBlank()&&id.length()<=36,400,"代理编号不合法");
        return one(db.select("SELECT id,tenant_id,ip_type,web_type,protocol,recycled,expires_at,relay_binding_revision FROM device WHERE id=? AND ip_type=2"+(lock?" FOR UPDATE":""),id));
    }
    private boolean eligible(Map<String,Object> device) {
        return number(device,"ipType")==2&&number(device,"webType")==2&&PROTOCOLS.contains(text(device,"protocol").toLowerCase(Locale.ROOT))&&!flag(device,"recycled")
            &&(device.get("expiresAt")==null||Instant.parse(text(device,"expiresAt")).isAfter(Instant.now()));
    }
    private List<Map<String,Object>> available(Map<String,Object> device) {
        if(!eligible(device))return List.of();
        return rows(db.select("SELECT "+NODE+" FROM relay_node n JOIN relay_pool p ON p.id=n.pool_id JOIN relay_pool_tenant g ON g.pool_id=p.id AND g.tenant_id=? WHERE "+VALID+" ORDER BY p.id,n.sort_order,n.id",device.get("tenantId")));
    }
    private List<Map<String,Object>> bound(Map<String,Object> device) {
        var values=rows(db.select("SELECT "+NODE+",b.sort_order AS binding_order,b.created_at AS bound_at FROM device_relay_node b JOIN relay_node n ON n.id=b.node_id JOIN relay_pool p ON p.id=n.pool_id LEFT JOIN relay_pool_tenant g ON g.pool_id=p.id AND g.tenant_id=? WHERE b.device_id=? ORDER BY b.sort_order,n.id",device.get("tenantId"),device.get("id")));
        if(!eligible(device))for(var value:values)value.put("available",false);
        return values;
    }
    private Map<String,Object> view(Map<String,Object> device) {
        return Map.of("revision",number(device,"relayBindingRevision"),"items",bound(device),"available",available(device));
    }
    // The device row lock makes revision and bindings one consistent read, even on READ COMMITTED.
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> get(ConfigOperator actor,String id) {
        operators.requireActive(actor);return view(device(id,true));
    }
    private List<String> current(Map<String,Object> device) {
        return rows(db.select("SELECT node_id FROM device_relay_node WHERE device_id=? ORDER BY sort_order,node_id",device.get("id"))).stream().map(row->text(row,"nodeId")).toList();
    }
    private void lockGrantedPools(Collection<Map<String,Object>> devices) {
        var tenants=devices.stream().map(d->text(d,"tenantId")).distinct().sorted().toList();
        // Pool writers hold the same lock; grant/node edits cannot race a successful bind validation.
        db.select("SELECT p.id FROM relay_pool p WHERE EXISTS(SELECT 1 FROM relay_pool_tenant g WHERE g.pool_id=p.id AND g.tenant_id IN ("+placeholders(tenants.size())+")) ORDER BY p.id FOR UPDATE",tenants.toArray());
    }
    private void validate(Map<String,Object> device,List<String> nodeIds) {
        if(nodeIds.isEmpty())return; // Always allow operators to remove obsolete bindings.
        require(eligible(device),400,"只有可用的静态 HTTP/HTTPS/SOCKS5/SS/VMess/VLESS 自有代理可绑定中继");
        Set<String> valid=new HashSet<>();for(var node:available(device))valid.add(text(node,"id"));
        require(valid.containsAll(nodeIds),409,"所选中继未获企业授权、授权已过期，或节点/分组已停用，请刷新后重试");
    }
    private Map<String,Object> apply(ConfigOperator actor,Map<String,Object> device,List<String> ids,String action) {
        var before=current(device);long revision=number(device,"relayBindingRevision");
        if(!before.equals(ids)) {
            db.execute("DELETE FROM device_relay_node WHERE device_id=?",device.get("id"));
            for(int index=0;index<ids.size();index++)db.execute("INSERT INTO device_relay_node(device_id,node_id,sort_order) VALUES(?,?,?)",device.get("id"),ids.get(index),index);
            db.execute("UPDATE device SET relay_binding_revision=relay_binding_revision+1 WHERE id=?",device.get("id"));revision++;
            db.execute("INSERT INTO config_audit(id,config_key,operator_id,operator_name,revision,before_json,after_json) VALUES(?,?,?,?,?,?,?)",
                id(),"proxy-relay-bindings",actor.id(),actor.username(),revision,json(Map.of("deviceId",device.get("id"),"nodeIds",before,"action",action)),json(Map.of("deviceId",device.get("id"),"nodeIds",ids,"action",action)));
        }
        device.put("relayBindingRevision",revision);
        return Map.of("deviceId",device.get("id"),"revision",revision,"nodeIds",ids);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> put(ConfigOperator actor,String id,Map<String,Object> input) {
        operators.requireActive(actor);allowed(input,Set.of("revision","nodeIds"));
        var value=device(id,true);require(integer(input.get("revision"),1,Long.MAX_VALUE)==number(value,"relayBindingRevision"),409,"代理中继绑定已被修改，请刷新后重试");
        var ids=ids(input.get("nodeIds"),8,true);lockGrantedPools(List.of(value));validate(value,ids);apply(actor,value,ids,"replace");return view(value);
    }
    private List<Map<String,Object>> devices(Map<String,Object> input) {
        var ids=ids(input.get("deviceIds"),100,false);var locked=new LinkedHashMap<String,Map<String,Object>>();
        for(String id:ids.stream().sorted().toList())locked.put(id,device(id,true));
        if(input.containsKey("revisions")) {
            require(input.get("revisions") instanceof Map<?,?>,400,"代理版本须为对象");var revisions=(Map<?,?>)input.get("revisions");
            require(revisions.size()==ids.size()&&revisions.keySet().containsAll(ids),400,"请提供每个代理的当前绑定版本");
            for(String id:ids)require(integer(revisions.get(id),1,Long.MAX_VALUE)==number(locked.get(id),"relayBindingRevision"),409,"代理中继绑定已被修改，请刷新后重试");
        }
        return ids.stream().map(locked::get).toList();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> batch(ConfigOperator actor,Map<String,Object> input) {
        operators.requireActive(actor);allowed(input,Set.of("deviceIds","nodeIds","mode","revisions"));
        String mode=text(input,"mode");require(Set.of("replace","append").contains(mode),400,"绑定模式须为 replace 或 append");
        var selected=ids(input.get("nodeIds"),8,true);var devices=devices(input);lockGrantedPools(devices);
        var plans=new ArrayList<List<String>>();
        for(var device:devices) {
            var merged=new LinkedHashSet<String>();if(mode.equals("append"))merged.addAll(current(device));merged.addAll(selected);
            require(merged.size()<=8,400,"每个代理最多绑定 8 个中继节点");var plan=List.copyOf(merged);validate(device,plan);plans.add(plan);
        }
        var results=new ArrayList<Map<String,Object>>();for(int i=0;i<devices.size();i++)results.add(apply(actor,devices.get(i),plans.get(i),mode));
        return Map.of("items",results);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> allocate(ConfigOperator actor,Map<String,Object> input) {
        operators.requireActive(actor);allowed(input,Set.of("deviceIds","count","revisions"));int count=(int)integer(input.get("count"),1,4);
        var devices=devices(input);lockGrantedPools(devices);
        var loads=new HashMap<String,Long>();
        for(var row:rows(db.select("SELECT node_id,COUNT(*) AS total FROM device_relay_node GROUP BY node_id")))loads.put(text(row,"nodeId"),number(row,"total"));
        // Allocation replaces selected devices' bindings; remove their previous load before planning.
        for(var device:devices)for(String id:current(device))loads.computeIfPresent(id,(key,value)->value-1);
        var plans=new ArrayList<List<String>>();
        for(var device:devices) {
            require(eligible(device),400,"所选代理不支持中继绑定");var choices=new ArrayList<>(available(device));
            require(choices.size()>=count,409,"企业可用中继数量不足，未修改任何代理绑定");
            choices.sort(Comparator.<Map<String,Object>>comparingLong(n->loads.getOrDefault(text(n,"id"),0L)).thenComparing(n->text(n,"poolId")).thenComparingLong(n->number(n,"sortOrder")).thenComparing(n->text(n,"id")));
            var plan=choices.subList(0,count).stream().map(n->text(n,"id")).toList();plans.add(plan);
            for(String id:plan)loads.merge(id,1L,Long::sum);
        }
        var results=new ArrayList<Map<String,Object>>();for(int i=0;i<devices.size();i++)results.add(apply(actor,devices.get(i),plans.get(i),"allocate"));
        return Map.of("items",results);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> nodeBindings(ConfigOperator actor,String nodeId,int page,int size) {
        operators.requireActive(actor);require(page>0&&size>0&&size<=100,400,"分页参数不合法");
        one(db.select("SELECT id FROM relay_node WHERE id=?",nodeId));
        long total=number(one(db.select("SELECT COUNT(*) AS total FROM device_relay_node WHERE node_id=?",nodeId)),"total");
        var values=rows(db.select("SELECT d.id,d.name,d.tenant_id,t.name AS tenant_name,d.protocol,d.host,d.port,d.web_type,d.recycled,d.expires_at,d.relay_binding_revision,b.sort_order,b.created_at AS bound_at FROM device_relay_node b JOIN device d ON d.id=b.device_id LEFT JOIN tenant t ON t.id=d.tenant_id WHERE b.node_id=? ORDER BY d.tenant_id,d.id LIMIT ? OFFSET ?",nodeId,size,(long)(page-1)*size));
        return Map.of("items",values,"total",total);
    }
    private static List<String> ids(Object raw,int max,boolean empty) {
        require(raw instanceof List<?>,400,"编号列表格式不合法");var list=(List<?>)raw;
        require(list.size()<=max&&(empty||!list.isEmpty()),400,"编号列表数量超出范围");var result=new ArrayList<String>();
        for(Object id:list){require(id instanceof String&&!((String)id).isBlank()&&((String)id).length()<=36,400,"编号格式不合法");require(!result.contains(id),400,"编号不能重复");result.add((String)id);}
        return result;
    }
    private static long integer(Object raw,long min,long max) {
        require(raw instanceof Number&&raw.toString().matches("[0-9]+"),400,"版本和数量须为整数");
        long value;try{value=Long.parseLong(raw.toString());}catch(NumberFormatException e){throw new BusinessException(400,"数值超出范围");}
        require(value>=min&&value<=max,400,"数值超出范围");return value;
    }
    private static void allowed(Map<String,Object> input,Set<String> keys){require(input!=null&&keys.containsAll(input.keySet()),400,"绑定请求包含不支持的字段");}
    private static String placeholders(int count){return String.join(",",Collections.nCopies(count,"?"));}
}
