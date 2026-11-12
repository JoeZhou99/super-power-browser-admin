package com.superpower.operations.proxy;

import com.superpower.operations.config.ConfigOperator;
import com.superpower.operations.config.ConfigOperatorAuth;
import com.superpower.operations.shared.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.superpower.operations.shared.Data.*;
import static com.superpower.operations.shared.BusinessException.require;

/** Read-only customer inventory. Never fetch credentials, resolve customer hosts or dial proxies. */
@Service
public class CustomerProxies {
    private final Db db;
    private final ConfigOperatorAuth operators;
    public CustomerProxies(Db db,ConfigOperatorAuth operators) { this.db=db;this.operators=operators; }
    private static final String GRANT="g.tenant_id=d.tenant_id AND g.enabled=TRUE AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP)";
    private static final String VALID_BOUND="EXISTS(SELECT 1 FROM device_relay_node b JOIN relay_node n ON n.id=b.node_id AND n.enabled=TRUE JOIN relay_pool p ON p.id=n.pool_id AND p.enabled=TRUE JOIN relay_pool_tenant g ON g.pool_id=p.id WHERE b.device_id=d.id AND "+GRANT+")";
    private static final String STATUS="CASE WHEN d.recycled=TRUE OR d.expires_at<=CURRENT_TIMESTAMP THEN 'inactive' "
        +"WHEN d.web_type<>2 OR LOWER(d.protocol) NOT IN ('http','https','socks5','ss','vmess','vless') THEN 'unsupported' "
        +"WHEN NOT EXISTS(SELECT 1 FROM device_relay_node b WHERE b.device_id=d.id) THEN 'direct' "
        +"WHEN NOT "+VALID_BOUND+" THEN 'unavailable' ELSE 'auto' END";
    private static final String COLUMNS="d.id,d.name,d.tenant_id,t.name AS tenant_name,d.created_by,COALESCE(NULLIF(u.display_name,''),u.username,'') AS creator_name,"
        +"d.host,d.port,d.protocol,d.username,d.relay_binding_revision,(SELECT COUNT(*) FROM device_relay_node b WHERE b.device_id=d.id) AS relay_count,CASE WHEN d.password<>'' THEN TRUE ELSE FALSE END AS has_password,d.region,d.provider,d.map_ip,d.map_port,d.web_type,d.expires_at,d.recycled,d.created_at,"
        +"(SELECT COUNT(*) FROM environment e WHERE e.device_id=d.id AND e.tenant_id=d.tenant_id) AS bound_count,"
        +"d.check_connected,d.check_ip,d.check_country_name,d.check_latency_ms,d.check_message,d.checked_at,"+STATUS+" AS acceleration_status";
    private static final String FROM=" FROM device d LEFT JOIN tenant t ON t.id=d.tenant_id LEFT JOIN app_user u ON u.id=d.created_by AND u.tenant_id=d.tenant_id";
    private static final List<String> FIELDS=List.of("id","name","tenantId","tenantName","createdBy","creatorName","host","port","protocol","username","hasPassword","region","provider","mapIp","mapPort","webType","expiresAt","recycled","createdAt","boundCount","checkConnected","checkIp","checkCountryName","checkLatencyMs","checkMessage","checkedAt","relayBindingRevision","relayCount");
    private static final Set<String> PROTOCOLS=Set.of("http","https","socks5","socks4","socks4a","ssh","ssr","ss","vmess","vless");
    private static final Map<String,String> REASONS=Map.of("auto","available","direct","no_bindings","unsupported","unsupported_proxy","unavailable","no_available_bound_nodes","inactive","inactive_device");

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> list(ConfigOperator actor,Map<String,String> query) {
        operators.requireActive(actor);
        require(Set.of("query","tenantId","protocol","webType","recycled","connectionStatus","accelerationStatus","page","size").containsAll(query.keySet()),400,"不支持的代理筛选项");
        int page=integer(query,"page",1,1,Integer.MAX_VALUE),size=integer(query,"size",20,1,100);
        StringBuilder where=new StringBuilder(" WHERE d.ip_type=2");var args=new ArrayList<Object>();
        String search=value(query,"query","");require(search.length()<=200,400,"代理关键词最多 200 字符");
        if(!search.isEmpty()) {
            where.append(" AND (d.name LIKE ? OR d.host LIKE ? OR d.id LIKE ? OR d.username LIKE ?)");
            for(int i=0;i<4;i++)args.add("%"+search+"%");
        }
        String tenant=value(query,"tenantId","");require(tenant.length()<=36,400,"企业编号不合法");
        if(!tenant.isEmpty()){where.append(" AND d.tenant_id=?");args.add(tenant);}
        String protocol=value(query,"protocol","").toLowerCase(Locale.ROOT);
        if(!protocol.isEmpty()){require(PROTOCOLS.contains(protocol),400,"代理协议不合法");where.append(" AND LOWER(d.protocol)=?");args.add(protocol);}
        String webType=value(query,"webType","");
        if(!webType.isEmpty()) {
            require(Set.of("0","1","2").contains(webType),400,"网络类型不合法");
            if(!webType.equals("0")){where.append(" AND d.web_type=?");args.add(Integer.parseInt(webType));}
        }
        String recycled=value(query,"recycled","");
        if(!recycled.isEmpty()){require(Set.of("true","false").contains(recycled),400,"回收状态不合法");where.append(" AND d.recycled=?");args.add(Boolean.parseBoolean(recycled));}
        String connection=value(query,"connectionStatus","all");require(Set.of("all","connected","failed","unchecked").contains(connection),400,"连接状态不合法");
        if(connection.equals("unchecked"))where.append(" AND d.check_connected IS NULL");
        else if(!connection.equals("all")){where.append(" AND d.check_connected=?");args.add(connection.equals("connected"));}
        String acceleration=value(query,"accelerationStatus","all");require(acceleration.equals("all")||REASONS.containsKey(acceleration),400,"加速状态不合法");
        if(!acceleration.equals("all")){where.append(" AND (").append(STATUS).append(")=?");args.add(acceleration);}
        long total=number(one(db.select("SELECT COUNT(*) AS total FROM device d"+where,args.toArray())),"total");
        args.add(size);args.add((long)(page-1)*size);
        var records=rows(db.select("SELECT "+COLUMNS+FROM+where+" ORDER BY d.created_at DESC,d.id LIMIT ? OFFSET ?",args.toArray()));
        return Map.of("items",decorate(records),"total",total);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> get(ConfigOperator actor,String id) {
        operators.requireActive(actor);require(!id.isBlank()&&id.length()<=36,400,"代理编号不合法");
        var row=one(db.select("SELECT "+COLUMNS+FROM+" WHERE d.ip_type=2 AND d.id=?",id));
        var result=decorate(List.of(row)).get(0);
        var bindings=rows(db.select("SELECT e.id,e.name,e.platform,e.owner_id,COALESCE(NULLIF(u.display_name,''),u.username,'') AS owner_name,e.recycled,e.last_opened_at,e.created_at FROM environment e LEFT JOIN app_user u ON u.id=e.owner_id AND u.tenant_id=e.tenant_id WHERE e.device_id=? AND e.tenant_id=? ORDER BY e.created_at DESC,e.id",id,row.get("tenantId")));
        for(var binding:bindings)binding.putIfAbsent("lastOpenedAt",null);
        result.put("bindings",bindings);return result;
    }

    private List<Map<String,Object>> decorate(List<Map<String,Object>> records) {
        var eligible=records.stream().filter(row->text(row,"accelerationStatus").equals("auto")).map(row->text(row,"id")).toList();
        var byDevice=new HashMap<String,List<Map<String,Object>>>();
        if(!eligible.isEmpty()) {
            var nodes=rows(db.select("SELECT b.device_id,n.id,n.pool_id,p.name AS pool_name,n.name,n.region,n.protocol,n.host,n.port FROM device_relay_node b JOIN device d ON d.id=b.device_id JOIN relay_node n ON n.id=b.node_id JOIN relay_pool p ON p.id=n.pool_id JOIN relay_pool_tenant g ON g.pool_id=p.id AND g.tenant_id=d.tenant_id WHERE b.device_id IN ("+placeholders(eligible.size())+") AND n.enabled=TRUE AND p.enabled=TRUE AND g.enabled=TRUE AND (g.expires_at IS NULL OR g.expires_at>CURRENT_TIMESTAMP) ORDER BY b.device_id,b.sort_order,n.id",eligible.toArray()));
            for(var node:nodes) {
                var selected=byDevice.computeIfAbsent(text(node,"deviceId"),key->new ArrayList<>());node.remove("deviceId");
                if(selected.size()<8)selected.add(node);
            }
        }
        var result=new ArrayList<Map<String,Object>>();
        for(var row:records) {
            var item=new LinkedHashMap<String,Object>();for(String field:FIELDS)item.put(field,row.get(field));
            item.put("checkMessage",text(row,"checkMessage").replaceAll("(?i)([a-z][a-z0-9+.-]*://)[^\\s/@]+@","$1[redacted]@"));
            String status=text(row,"accelerationStatus");
            List<Map<String,Object>> candidates=byDevice.getOrDefault(text(row,"id"),List.of());
            var pools=new LinkedHashMap<String,Map<String,Object>>();
            for(var node:candidates)pools.put(text(node,"poolId"),Map.of("id",text(node,"poolId"),"name",text(node,"poolName")));
            item.put("relayAvailableCount",candidates.size());
            item.put("acceleration",Map.of("mode","bindings","status",status,"reason",REASONS.get(status),"pools",new ArrayList<>(pools.values()),"candidates",candidates,"directAvailable",!status.equals("inactive")));
            result.add(item);
        }
        return result;
    }
    private static String placeholders(int count) { return String.join(",",Collections.nCopies(count,"?")); }
    private static String value(Map<String,String> query,String key,String fallback) { String value=query.get(key);return value==null||value.isBlank()?fallback:value.trim(); }
    private static int integer(Map<String,String> query,String key,int fallback,int min,int max) {
        String raw=value(query,key,Integer.toString(fallback));require(raw.matches("[0-9]{1,10}"),400,"分页参数不合法");
        long parsed=Long.parseLong(raw);require(parsed>=min&&parsed<=max,400,"分页参数超出范围");return (int)parsed;
    }
}
