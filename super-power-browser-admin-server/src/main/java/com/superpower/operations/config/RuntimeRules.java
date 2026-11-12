package com.superpower.operations.config;

import com.superpower.operations.shared.BusinessException;
import java.net.IDN;
import java.net.URI;
import java.util.*;
import static com.superpower.operations.shared.BusinessException.require;

/** Configuration accepted by the independent desktop runtime. */
public final class RuntimeRules {
    private RuntimeRules() {}
    public static final Map<String,String> FIELDS;
    static {
        var fields=new LinkedHashMap<String,String>();
        fields.put("network-routing","networkRouting");fields.put("access-policy","accessPolicy");
        fields.put("network-checks","networkChecks");fields.put("passkey-rules","passkeyRules");
        FIELDS=Collections.unmodifiableMap(fields);
    }
    public static Map<String,Object> defaults(String key) {
        return switch(key) {
            case "network-routing" -> Map.of("enabled",false,"directDomains",List.of(),"resourceDomains",List.of(),"cdnDomains",List.of());
            case "access-policy" -> Map.of("enabled",false,"mode","blacklist","domains",List.of(),"exceptions",List.of());
            case "network-checks" -> Map.of("enabled",false,
                "proxyProviders",List.of("https://myip.ipip.net/","https://ifconfig.me/ip","https://lumtest.com/myip.json"),
                "localProviders",List.of("https://ipwho.is/","https://ipinfo.io/json","https://api.ipify.org?format=json"));
            case "passkey-rules" -> Map.of("enabled",false,"loginMatches",List.of(),"registerMatches",List.of());
            default -> throw new BusinessException(404,"配置项不存在");
        };
    }
    public static Map<String,Object> validate(String key,Map<String,Object> values) {
        require(values.keySet().equals(defaults(key).keySet()),400,"配置字段不正确");
        require(values.get("enabled") instanceof Boolean,400,"enabled 必须为布尔值");
        var result=new LinkedHashMap<String,Object>();result.put("enabled",values.get("enabled"));
        switch(key) {
            case "network-routing" -> {
                for(String field:List.of("directDomains","resourceDomains","cdnDomains"))result.put(field,strings(values,field,0,100,value->domain(value,true)));
            }
            case "access-policy" -> {
                require(values.get("mode") instanceof String&&Set.of("blacklist","whitelist").contains(values.get("mode")),400,"访问模式须为 blacklist 或 whitelist");
                result.put("mode",values.get("mode"));
                int minimum=Boolean.TRUE.equals(values.get("enabled"))&&"whitelist".equals(values.get("mode"))?1:0;
                result.put("domains",strings(values,"domains",minimum,100,value->domain(value,false)));
                result.put("exceptions",strings(values,"exceptions",0,100,RuntimeRules::exceptionPrefix));
            }
            case "network-checks" -> {
                for(String field:List.of("proxyProviders","localProviders"))result.put(field,strings(values,field,Boolean.TRUE.equals(values.get("enabled"))?1:0,10,RuntimeRules::providerUrl));
            }
            case "passkey-rules" -> {
                for(String field:List.of("loginMatches","registerMatches"))result.put(field,strings(values,field,Boolean.TRUE.equals(values.get("enabled"))?1:0,100,RuntimeRules::passkeyMatch));
            }
            default -> throw new BusinessException(404,"配置项不存在");
        }
        return result;
    }
    private static List<String> strings(Map<String,Object> source,String field,int min,int max,java.util.function.Function<String,String> validate) {
        require(source.get(field) instanceof List<?>,400,field+" 须为列表");
        var input=(List<?>)source.get(field);require(input.size()>=min&&input.size()<=max,400,field+" 数量须为 "+min+" 至 "+max);
        var result=new ArrayList<String>();var seen=new HashSet<String>();
        for(Object raw:input) {
            require(raw instanceof String,400,field+" 每项必须为文本");String value=((String)raw).trim();
            require(!value.isEmpty()&&value.length()<=2048&&value.chars().noneMatch(Character::isISOControl),400,field+" 包含无效值");
            value=validate.apply(value);require(seen.add(value),400,field+" 存在重复项");result.add(value);
        }
        return result;
    }
    private static String domain(String input,boolean wildcard) {
        String value=input.toLowerCase(Locale.ROOT);boolean star=value.startsWith("*.");
        require(!star||wildcard,400,"此规则不支持通配符域名");if(star)value=value.substring(2);
        try { value=IDN.toASCII(value,IDN.USE_STD3_ASCII_RULES); }
        catch(IllegalArgumentException e) { throw new BusinessException(400,"请输入公共纯域名，不含协议、路径、端口或 IP 地址"); }
        require(value.length()<=253&&value.matches("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?"),400,"请输入公共纯域名，不含协议、路径、端口或 IP 地址");
        String suffix=value.substring(value.lastIndexOf('.')+1);
        require(suffix.length()>=2&&!Set.of("localhost","local","localdomain","lan","internal","test","invalid","onion").contains(suffix)
            &&!value.equals("home.arpa")&&!value.endsWith(".home.arpa"),400,"请输入公共域名");
        return (star?"*.":"")+value;
    }
    private static URI httpsUrl(String input) {
        try {
            var url=URI.create(input);
            require("https".equals(url.getScheme())&&url.getHost()!=null&&url.getRawUserInfo()==null&&url.getRawFragment()==null
                &&(url.getPort()==-1||url.getPort()==443)&&!input.contains("\\"),400,"请输入不含凭据或片段、使用标准端口的 HTTPS 地址");
            domain(url.getHost(),false);
            return url;
        }catch(IllegalArgumentException e){throw new BusinessException(400,"HTTPS 地址格式不正确");}
    }
    private static String normalizedUrl(URI url,boolean prefix) {
        require(!prefix||url.getRawQuery()==null,400,"例外 URL 前缀不能包含查询参数");
        String path=url.normalize().getRawPath();if(path==null||path.isEmpty())path=prefix?"/":"";
        return "https://"+url.getHost().toLowerCase(Locale.ROOT)+path+(url.getRawQuery()==null?"":"?"+url.getRawQuery());
    }
    private static String providerUrl(String value) { return normalizedUrl(httpsUrl(value),false); }
    private static String exceptionPrefix(String value) {
        require(!value.contains("*"),400,"例外 URL 前缀不支持通配符");return normalizedUrl(httpsUrl(value),true);
    }
    private static String passkeyMatch(String value) {
        require(value.startsWith("https://"),400,"Passkey 规则须使用 HTTPS");
        int slash=value.indexOf('/',8);require(slash>8,400,"Passkey 规则必须包含具体域名和路径");
        String host=domain(value.substring(8,slash),true),path=value.substring(slash);
        require(path.chars().noneMatch(Character::isWhitespace)&&!path.contains("\\")&&!path.contains("#"),400,"Passkey 路径格式不正确");
        return "https://"+host+path;
    }
}
