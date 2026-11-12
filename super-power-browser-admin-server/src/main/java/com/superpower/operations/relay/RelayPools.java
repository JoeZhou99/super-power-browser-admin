package com.superpower.operations.relay;

import com.superpower.operations.config.*;
import com.superpower.operations.shared.*;
import java.net.IDN;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.superpower.operations.shared.Data.*;
import static com.superpower.operations.shared.BusinessException.require;

/** Platform-owned authenticated relay inventory. No server-side dialing or customer-supplied nodes. */
@Service
public class RelayPools {
    private static final Set<String> NODE_PROTOCOLS=Set.of("http","https","socks5","ss");
    // AEAD subset of desktop ProxyConfig.SS_METHODS; never permit unencrypted none/plain relays.
    private static final Set<String> SS_AEAD_METHODS=Set.of("aes-128-gcm","aes-256-gcm","chacha20-poly1305","chacha20-ietf-poly1305","xchacha20-poly1305","xchacha20-ietf-poly1305","2022-blake3-aes-128-gcm","2022-blake3-aes-256-gcm","2022-blake3-chacha20-poly1305");
    private final Db db;
    private final ConfigOperatorAuth operators;
    private final RelaySecrets secrets;
    public RelayPools(Db db,ConfigOperatorAuth operators,RelaySecrets secrets) { this.db=db;this.operators=operators;this.secrets=secrets; }
    private static final String POOL="p.id,p.name,p.code,p.remark,p.enabled,p.candidate_limit,p.revision,p.updated_at,(SELECT COUNT(*) FROM relay_node n WHERE n.pool_id=p.id) AS node_count,(SELECT COUNT(*) FROM relay_pool_tenant g WHERE g.pool_id=p.id) AS grant_count";
    private static final String NODE="id,pool_id,name,region,protocol,host,port,username,CASE WHEN password<>'' THEN TRUE ELSE FALSE END AS has_password,proxy_options,enabled,sort_order,revision,updated_at,(SELECT COUNT(*) FROM device_relay_node counted WHERE counted.node_id=relay_node.id) AS bound_proxy_count";
    public Map<String,Object> list(ConfigOperator actor) { operators.requireActive(actor);return Map.of("items",rows(db.select("SELECT "+POOL+" FROM relay_pool p ORDER BY p.name,p.id"))); }
    public Map<String,Object> get(ConfigOperator actor,String id) { operators.requireActive(actor);return one(db.select("SELECT "+POOL+" FROM relay_pool p WHERE p.id=?",id)); }
    private Map<String,Object> lock(ConfigOperator actor,String id) {
        operators.requireActive(actor);return one(db.select("SELECT * FROM relay_pool WHERE id=? FOR UPDATE",id));
    }
    private void revision(Map<String,Object> before,Map<String,Object> input) {
        require(integer(input,"revision",1,Long.MAX_VALUE)==number(before,"revision"),409,"线路配置已被其他管理员修改，请刷新后重试");
    }
    private void changed(String id) { db.execute("UPDATE relay_pool SET revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",id); }
    private void audit(ConfigOperator actor,String action,String poolId,Object before,Object after) {
        long version=number(one(db.select("SELECT revision FROM relay_pool WHERE id=?",poolId)),"revision");
        db.execute("INSERT INTO config_audit(id,config_key,operator_id,operator_name,revision,before_json,after_json) VALUES(?,?,?,?,?,?,?)",
            id(),"relay-pools",actor.id(),actor.username(),version,json(Map.of("action",action,"poolId",poolId,"value",before)),json(Map.of("action",action,"poolId",poolId,"value",after)));
    }
    @Transactional public Map<String,Object> create(ConfigOperator actor,Map<String,Object> input) {
        operators.requireActive(actor);allowed(input,Set.of("name","code","remark","enabled","candidateLimit"));
        String id=id(),name=string(input,"name",120);boolean enabled=bool(input,"enabled",true);
        long limit=integer(input,"candidateLimit",1,4);
        db.execute("INSERT INTO relay_pool(id,name,code,remark,enabled,candidate_limit) VALUES(?,?,?,?,?,?)",id,name,code(input,id),optionalString(input,"remark",1000),enabled,limit);
        var result=get(actor,id);audit(actor,"create-pool",id,Map.of(),result);return result;
    }
    @Transactional public Map<String,Object> update(ConfigOperator actor,String id,Map<String,Object> input) {
        allowed(input,Set.of("revision","name","code","remark","enabled","candidateLimit"));var before=lock(actor,id);revision(before,input);
        db.execute("UPDATE relay_pool SET name=?,code=?,remark=?,enabled=?,candidate_limit=? WHERE id=?",string(input,"name",120),code(input,text(before,"code")),input.containsKey("remark")?optionalString(input,"remark",1000):text(before,"remark"),bool(input,"enabled",true),integer(input,"candidateLimit",1,4),id);
        changed(id);var result=get(actor,id);audit(actor,"update-pool",id,before,result);return result;
    }
    public Map<String,Object> nodes(ConfigOperator actor,String poolId) {
        get(actor,poolId);return Map.of("items",rows(db.select("SELECT "+NODE+" FROM relay_node WHERE pool_id=? ORDER BY sort_order,id",poolId)).stream().map(this::nodeView).toList());
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String,Object> allNodes(ConfigOperator actor,Map<String,String> query) {
        operators.requireActive(actor);require(Set.of("query","poolId","enabled","protocol","page","size").containsAll(query.keySet()),400,"不支持的中继筛选项");
        String search=query.getOrDefault("query","").trim(),poolId=query.getOrDefault("poolId","").trim();
        require(search.length()<=120&&poolId.length()<=36,400,"中继筛选条件过长");
        String enabled=query.getOrDefault("enabled","all"),protocol=query.getOrDefault("protocol","").toLowerCase(Locale.ROOT);
        require(Set.of("all","true","false").contains(enabled),400,"中继启用状态不合法");
        require(protocol.isBlank()||NODE_PROTOCOLS.contains(protocol),400,"中继协议不合法");
        long page=pageNumber(query,"page",1,Integer.MAX_VALUE),size=pageNumber(query,"size",20,100);
        var where=new StringBuilder(" WHERE 1=1");var args=new ArrayList<Object>();
        if(!search.isEmpty()) {
            where.append(" AND (n.name LIKE ? OR n.host LIKE ? OR n.region LIKE ? OR p.name LIKE ? OR p.code LIKE ?)");
            for(int i=0;i<5;i++)args.add("%"+search+"%");
        }
        if(!poolId.isEmpty()){where.append(" AND n.pool_id=?");args.add(poolId);}
        if(!enabled.equals("all")){where.append(" AND n.enabled=?");args.add(Boolean.parseBoolean(enabled));}
        if(!protocol.isBlank()){where.append(" AND n.protocol=?");args.add(protocol);}
        String from=" FROM relay_node n JOIN relay_pool p ON p.id=n.pool_id";
        long total=number(one(db.select("SELECT COUNT(*) AS total"+from+where,args.toArray())),"total");
        args.add(size);args.add((page-1)*size);
        String columns="n.id,n.pool_id,n.name,n.region,n.protocol,n.host,n.port,n.username,CASE WHEN n.password<>'' THEN TRUE ELSE FALSE END AS has_password,n.proxy_options,n.enabled,n.sort_order,n.revision,n.updated_at,p.name AS pool_name,p.code AS pool_code,p.enabled AS pool_enabled,(SELECT COUNT(*) FROM device_relay_node counted WHERE counted.node_id=n.id) AS bound_proxy_count";
        var values=rows(db.select("SELECT "+columns+from+where+" ORDER BY p.id,n.sort_order,n.id LIMIT ? OFFSET ?",args.toArray()));
        return Map.of("items",values.stream().map(this::nodeView).toList(),"total",total);
    }
    private static long pageNumber(Map<String,String> query,String key,long fallback,long max) {
        String raw=query.getOrDefault(key,Long.toString(fallback));require(raw.matches("[0-9]{1,10}"),400,"分页参数不合法");
        long value=Long.parseLong(raw);require(value>=1&&value<=max,400,"分页参数超出范围");return value;
    }
    private static String code(Map<String,Object> input,String fallback) {
        String value=optionalString(input,"code",64).trim();if(value.isEmpty())value=fallback;
        require(value.matches("[a-zA-Z0-9_-]{1,64}"),400,"分组编码仅支持 1 至 64 位字母、数字、下划线和短横线");return value;
    }
    private Map<String,Object> nodeView(Map<String,Object> value) {
        var result=new LinkedHashMap<String,Object>(value);result.put("proxyOptions",object(text(value,"proxyOptions")));return result;
    }
    private Map<String,Object> node(String poolId,String id) { return nodeView(one(db.select("SELECT "+NODE+" FROM relay_node WHERE pool_id=? AND id=?",poolId,id))); }
    @Transactional public Map<String,Object> saveNode(ConfigOperator actor,String poolId,String nodeId,Map<String,Object> input) {
        allowed(input,Set.of("revision","name","region","protocol","host","port","username","password","proxyOptions","enabled","sortOrder"));
        lock(actor,poolId);boolean creating=nodeId==null;
        var before=creating?Map.<String,Object>of():node(poolId,nodeId);
        if(!creating)revision(before,input);
        if(creating)require(number(one(db.select("SELECT COUNT(*) AS total FROM relay_node WHERE pool_id=?",poolId)),"total")<100,400,"每个线路组最多维护 100 个中继节点");
        String name=string(input,"name",120),region=optionalString(input,"region",150),protocol=string(input,"protocol",10).toLowerCase(Locale.ROOT);
        require(NODE_PROTOCOLS.contains(protocol),400,"中继协议支持 HTTP、HTTPS、SOCKS5 和 Shadowsocks");
        String host=host(string(input,"host",255)),username=optionalString(input,"username",255);long port=integer(input,"port",1,65535);
        require(protocol.equals("ss")||!username.isBlank(),400,"共享中继必须设置认证账号");
        require(username.getBytes(StandardCharsets.UTF_8).length<=255,400,"中继账号最多 255 字节");
        if(!creating&&protocol.equals("socks5")&&!text(before,"protocol").equals(protocol))require(input.containsKey("password"),400,"切换为 SOCKS5 时请重新填写密码，以验证 255 字节限制");
        String encrypted=creating?"":text(one(db.select("SELECT password FROM relay_node WHERE pool_id=? AND id=?",poolId,nodeId)),"password");
        if(creating||input.containsKey("password")) {
            String password=optionalString(input,"password",600);require(!password.isEmpty(),400,"共享中继必须设置认证密码");
            require(password.getBytes(StandardCharsets.UTF_8).length<=600,400,"中继密码最多 600 字节");
            if(protocol.equals("socks5"))require(password.getBytes(StandardCharsets.UTF_8).length<=255,400,"SOCKS5 密码最多 255 字节");
            encrypted=secrets.encrypt(password);
        }
        require(!encrypted.isBlank(),400,"共享中继必须设置认证密码");
        Object raw=input.containsKey("proxyOptions")?input.get("proxyOptions"):before.getOrDefault("proxyOptions",Map.of());
        var options=options(protocol,raw);boolean enabled=bool(input,"enabled",true);long order=integer(input,"sortOrder",0,1000000);
        String existingId=creating?"":nodeId;
        require(db.select("SELECT id FROM relay_node WHERE pool_id=? AND host=? AND port=? AND id<>?",poolId,host,port,existingId).isEmpty(),409,"此线路组已存在相同地址和端口的节点");
        if(creating) {
            nodeId=id();db.execute("INSERT INTO relay_node(id,pool_id,name,region,protocol,host,port,username,password,proxy_options,enabled,sort_order) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                nodeId,poolId,name,region,protocol,host,port,username,encrypted,json(options),enabled,order);
        } else db.execute("UPDATE relay_node SET name=?,region=?,protocol=?,host=?,port=?,username=?,password=?,proxy_options=?,enabled=?,sort_order=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE pool_id=? AND id=?",
            name,region,protocol,host,port,username,encrypted,json(options),enabled,order,poolId,nodeId);
        changed(poolId);var result=node(poolId,nodeId);
        // Audit receives only public views; passwords and ciphertext never enter the generic config log.
        var audited=new LinkedHashMap<String,Object>(result);audited.put("credentialChanged",creating||input.containsKey("password")||!username.equals(text(before,"username")));
        audit(actor,creating?"create-node":"update-node",poolId,before,audited);return result;
    }
    @Transactional public Map<String,Object> grants(ConfigOperator actor,String poolId) {
        var pool=lock(actor,poolId);return Map.of("revision",number(pool,"revision"),"items",rows(db.select("SELECT g.tenant_id,t.name AS tenant_name,g.enabled,g.expires_at FROM relay_pool_tenant g JOIN tenant t ON t.id=g.tenant_id WHERE g.pool_id=? ORDER BY t.name,t.id",poolId)));
    }
    @Transactional public Map<String,Object> saveGrants(ConfigOperator actor,String poolId,Map<String,Object> input) {
        allowed(input,Set.of("revision","items"));var before=lock(actor,poolId);revision(before,input);
        require(input.get("items") instanceof List<?>,400,"授权企业必须为列表");var items=(List<?>)input.get("items");require(items.size()<=2000,400,"每次最多保存 2000 条企业授权");
        var seen=new HashSet<String>();var values=new ArrayList<Map<String,Object>>();
        for(Object entry:items) {
            var item=map(entry);allowed(item,Set.of("tenantId","enabled","expiresAt"));String tenant=string(item,"tenantId",36);
            require(seen.add(tenant),400,"企业授权重复");require(!db.select("SELECT id FROM tenant WHERE id=?",tenant).isEmpty(),400,"授权企业不存在");
            var value=new LinkedHashMap<String,Object>();value.put("tenantId",tenant);value.put("enabled",bool(item,"enabled",true));value.put("expiresAt",expiry(item.get("expiresAt")));values.add(value);
        }
        var old=grants(actor,poolId);db.execute("DELETE FROM relay_pool_tenant WHERE pool_id=?",poolId);
        for(var item:values)db.execute("INSERT INTO relay_pool_tenant(pool_id,tenant_id,enabled,expires_at) VALUES(?,?,?,?)",poolId,item.get("tenantId"),item.get("enabled"),item.get("expiresAt"));
        changed(poolId);audit(actor,"update-grants",poolId,old,grants(actor,poolId));return get(actor,poolId);
    }
    public Map<String,Object> tenants(ConfigOperator actor,String query) {
        operators.requireActive(actor);require(query.length()<=120,400,"企业搜索过长");
        return Map.of("items",rows(db.select("SELECT id,name FROM tenant WHERE name LIKE ? OR id=? ORDER BY name,id LIMIT 100","%"+query.trim()+"%",query.trim())));
    }
    private static Object expiry(Object input) {
        if(input==null||"".equals(input))return null;require(input instanceof String,400,"授权有效期必须为 ISO 时间");
        try{return Timestamp.from(Instant.parse((String)input));}catch(Exception e){throw new BusinessException(400,"授权有效期必须为 ISO 时间");}
    }
    private static Map<String,Object> options(String protocol,Object raw) {
        var input=map(raw);
        if(protocol.equals("ss")) {
            allowed(input,Set.of("encryption"));
            String encryption=string(input,"encryption",64).toLowerCase(Locale.ROOT);
            require(SS_AEAD_METHODS.contains(encryption),400,"请选择支持的 Shadowsocks AEAD 加密方式");
            return Map.of("encryption",encryption);
        }
        allowed(input,Set.of("tls"));if(input.isEmpty())return Map.of();
        require(protocol.equals("https"),400,"只有 HTTPS 中继可设置 TLS 参数");var tls=map(input.get("tls"));allowed(tls,Set.of("serverName","allowInsecure"));
        var out=new LinkedHashMap<String,Object>();String name=optionalString(tls,"serverName",255);if(!name.isBlank())out.put("serverName",host(name));
        out.put("allowInsecure",bool(tls,"allowInsecure",false));return Map.of("tls",out);
    }
    private static String host(String raw) {
        require(!raw.isBlank()&&!raw.matches(".*[\\s/@?#\\\\].*")&&raw.length()<=255,400,"中继地址须为不含协议和端口的主机名或 IP");
        String value=raw.toLowerCase(Locale.ROOT);
        if(value.startsWith("[")&&value.endsWith("]"))value=value.substring(1,value.length()-1);
        if(value.contains(":")) {
            require(value.matches("[0-9a-f:.]+"),400,"IPv6 地址不合法");
            try{require(InetAddress.getByName(value).getAddress().length==16,400,"IPv6 地址不合法");}catch(Exception e){throw new BusinessException(400,"IPv6 地址不合法");}
            return value;
        }
        if(value.endsWith("."))value=value.substring(0,value.length()-1);
        try{value=IDN.toASCII(value,IDN.USE_STD3_ASCII_RULES);}catch(Exception e){throw new BusinessException(400,"中继主机名不合法");}
        require(value.length()<=253&&!value.isBlank(),400,"中继主机名不合法");
        for(String label:value.split("\\.",-1))require(!label.isEmpty()&&label.length()<=63&&!label.startsWith("-")&&!label.endsWith("-"),400,"中继主机名不合法");
        return value;
    }
    private static String string(Map<String,Object> input,String key,int max) { String value=optionalString(input,key,max).trim();require(!value.isBlank(),400,"请填写 "+key);return value; }
    private static String optionalString(Map<String,Object> input,String key,int max) {
        Object raw=input.getOrDefault(key,"");require(raw instanceof String,400,"字段须为文本："+key);String value=(String)raw;
        require(value.length()<=max&&!value.matches("(?s).*[\\x00-\\x1f\\x7f].*"),400,"字段长度或字符不合法："+key);return value;
    }
    private static boolean bool(Map<String,Object> input,String key,boolean fallback) {
        if(!input.containsKey(key))return fallback;require(input.get(key) instanceof Boolean,400,"字段须为布尔值："+key);return (Boolean)input.get(key);
    }
    private static long integer(Map<String,Object> input,String key,long min,long max) {
        require(input.get(key) instanceof Number&&text(input,key).matches("[0-9]+"),400,"字段须为整数："+key);long value=number(input,key);require(value>=min&&value<=max,400,"数值超出范围："+key);return value;
    }
    private static void allowed(Map<String,Object> input,Set<String> keys) { require(input!=null&&keys.containsAll(input.keySet()),400,"中继配置包含不支持的字段"); }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object raw) { require(raw instanceof Map<?,?>,400,"配置须为对象");var value=(Map<?,?>)raw;require(value.keySet().stream().allMatch(String.class::isInstance),400,"配置字段不合法");return (Map<String,Object>)value; }
}
