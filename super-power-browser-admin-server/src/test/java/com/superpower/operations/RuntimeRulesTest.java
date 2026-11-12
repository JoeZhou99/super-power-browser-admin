package com.superpower.operations;

import com.superpower.operations.config.RuntimeRules;
import com.superpower.operations.shared.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeRulesTest {
    private Map<String,Object> defaults(String key) { return new LinkedHashMap<>(RuntimeRules.defaults(key)); }
    private void bad(String key,Map<String,Object> values) { assertEquals(400,assertThrows(BusinessException.class,()->RuntimeRules.validate(key,values)).status); }
    @Test void disabledDefaultsDoNotChangeExistingDesktopBehavior() {
        for(String key:RuntimeRules.FIELDS.keySet()) {
            var values=RuntimeRules.defaults(key);assertEquals(false,values.get("enabled"));
            assertEquals(values,RuntimeRules.validate(key,values));
        }
    }
    @Test void booleansUnknownFieldsListTypesAndLimitsAreStrict() {
        for(String key:RuntimeRules.FIELDS.keySet()) {
            var value=defaults(key);value.put("enabled","true");bad(key,value);
            value=defaults(key);value.put("unknown",List.of());bad(key,value);
        }
        for(Object raw:List.of("amazon.com",List.of(3),Collections.nCopies(101,"amazon.com"),List.of(""))) {
            var value=defaults("network-routing");value.put("directDomains",raw);bad("network-routing",value);
        }
        var duplicate=defaults("network-routing");duplicate.put("directDomains",List.of("Amazon.com","amazon.com"));bad("network-routing",duplicate);
    }
    @Test void routingNormalizesDomainsAndRejectsNonDomainsOrPrivateHosts() {
        var value=defaults("network-routing");value.put("directDomains",List.of(" *.Amazon.COM ","seller.shopee.com.my"));
        assertEquals(List.of("*.amazon.com","seller.shopee.com.my"),RuntimeRules.validate("network-routing",value).get("directDomains"));
        for(String invalid:List.of("*","*.com","https://amazon.com","amazon.com/path","127.0.0.1","localhost","host.local","host.localdomain","host.lan","home.arpa","host.home.arpa","host.internal","host.test","amazon.com:443","user@amazon.com","bad_domain.com","amazon.com.")) {
            value.put("directDomains",List.of(invalid));bad("network-routing",value);
        }
    }
    @Test void accessDomainsHaveImplicitSubdomainsAndExceptionsAreCanonicalHttpsPrefixes() {
        var value=defaults("access-policy");value.put("enabled",true);value.put("mode","whitelist");bad("access-policy",value);value.put("domains",List.of("amazon.com"));
        value.put("exceptions",List.of("https://EXAMPLE.com:443","https://example.com/a/../checkout/"));
        assertEquals(List.of("https://example.com/","https://example.com/checkout/"),RuntimeRules.validate("access-policy",value).get("exceptions"));
        value.put("domains",List.of("*.amazon.com"));bad("access-policy",value);value.put("domains",List.of("amazon.com"));
        for(String url:List.of("http://example.com/","https://user:secret@example.com/","https://example.com/?a=1","https://example.com/#fragment","https://example.com:8443/","https://example.com\\@evil.com/","https://example.com/path/*")) {
            value.put("exceptions",List.of(url));bad("access-policy",value);
        }
    }
    @Test void enabledIpChecksRequirePublicHttpsProvidersButDisabledListsCanBeEmpty() {
        var value=defaults("network-checks");value.put("proxyProviders",List.of());value.put("localProviders",List.of());
        RuntimeRules.validate("network-checks",value);value.put("enabled",true);bad("network-checks",value);
        value.put("localProviders",List.of("https://ipwho.is/"));
        for(String url:List.of("http://ipinfo.io/json","https://127.0.0.1/","https://10.0.0.1/","https://[::1]/","https://localhost/","https://metadata.internal/","https://user@ipinfo.io/","https://ipinfo.io:8443/","https://ipinfo.io/#secret")) {
            value.put("proxyProviders",List.of(url));bad("network-checks",value);
        }
        value.put("proxyProviders",List.of("https://api.ipify.org?format=json"));
        assertEquals(List.of("https://api.ipify.org?format=json"),RuntimeRules.validate("network-checks",value).get("proxyProviders"));
    }
    @Test void passkeyUsesChromeMatchPatternsAndCannotEnableEmptyOverrides() {
        var value=defaults("passkey-rules");value.put("enabled",true);bad("passkey-rules",value);
        value.put("loginMatches",List.of("https://*.amazon.com/ap/signin*"));
        value.put("registerMatches",List.of("https://login.microsoftonline.com/common/oauth2/*"));
        assertEquals(value,RuntimeRules.validate("passkey-rules",value));
        for(String pattern:List.of("<all_urls>","https://*/*","http://*.amazon.com/*","https://*.amazon.com:443/*","https://user@amazon.com/*","https://*.amazon.com/*#fragment","https://amazon.com","https://127.0.0.1/*","https://*.com/*","https://amazon.com/a b","https://amazon.com/\\w+")) {
            value.put("loginMatches",List.of(pattern));bad("passkey-rules",value);
        }
    }
}
