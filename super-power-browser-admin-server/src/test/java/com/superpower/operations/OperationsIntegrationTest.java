package com.superpower.operations;

import com.superpower.operations.config.*;
import com.superpower.operations.shared.BusinessException;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static com.superpower.operations.shared.Data.*;

/** Real HTTP security, shared-schema adoption, MyBatis writes and database transactions. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationsIntegrationTest {
    private static final String PASSWORD="Synthetic-operations-123!";
    private final HttpClient http=HttpClient.newHttpClient();
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private BrowserConfiguration configs;
    private ConfigOperatorAuth auth;
    private ConfigOperator operator;
    private String base;
    private long businessHistoryRows;

    /** Isolated shared-schema fixture; production V28 remains owned by the business service. */
    private static class V28__device_relay_bindings extends BaseJavaMigration {
        @Override public void migrate(Context context) throws Exception {
            try(var statement=context.getConnection().createStatement()) {
                statement.execute("ALTER TABLE device ADD COLUMN relay_binding_revision BIGINT NOT NULL DEFAULT 1");
                statement.execute("""
                    CREATE TABLE device_relay_node (
                      device_id VARCHAR(36) NOT NULL, node_id VARCHAR(36) NOT NULL,
                      sort_order INT NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY(device_id,node_id), CONSTRAINT device_relay_position UNIQUE(device_id,sort_order),
                      CONSTRAINT device_relay_device_fk FOREIGN KEY(device_id) REFERENCES device(id) ON DELETE CASCADE,
                      CONSTRAINT device_relay_node_fk FOREIGN KEY(node_id) REFERENCES relay_node(id) ON DELETE CASCADE,
                      CONSTRAINT device_relay_order CHECK(sort_order>=0 AND sort_order<8)
                    )
                    """);
                statement.execute("CREATE INDEX device_relay_node_reverse ON device_relay_node(node_id,device_id)");
                statement.execute("CREATE TABLE kernel_catalog_lock (id INT PRIMARY KEY)");
                statement.execute("INSERT INTO kernel_catalog_lock(id) VALUES(1)");
                statement.execute("""
                    CREATE TABLE kernel_release (
                      id VARCHAR(36) PRIMARY KEY, version VARCHAR(30) NOT NULL, platform VARCHAR(20) NOT NULL,
                      arch VARCHAR(20) NOT NULL, full_version VARCHAR(50) NOT NULL, sha256 VARCHAR(64) NOT NULL,
                      archive_bytes BIGINT NOT NULL, descriptor CLOB NOT NULL, storage_type VARCHAR(10) NOT NULL,
                      storage_key VARCHAR(512) NOT NULL, object_version VARCHAR(200), state VARCHAR(16) NOT NULL,
                      recommended BOOLEAN NOT NULL DEFAULT FALSE, revision BIGINT NOT NULL DEFAULT 0,
                      created_by VARCHAR(36), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(version,platform,arch)
                    )
                    """);
            }
        }
    }

    @BeforeAll void start() {
        String url="jdbc:h2:mem:operations-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try(var source=new HikariDataSource()) {
            source.setJdbcUrl(url);source.setUsername("sa");source.setPassword("");
            Flyway.configure().dataSource(source).locations("classpath:db/legacy","classpath:db/relay").javaMigrations(new V28__device_relay_bindings()).load().migrate();
            var old=new JdbcTemplate(source);
            businessHistoryRows=old.queryForObject("SELECT COUNT(*) FROM flyway_schema_history",Long.class);
            old.update("INSERT INTO browser_config(config_key,payload,revision) VALUES('languages',?,7)",json(Map.of("entries",List.of(Map.of("value","zh-CN","label","Existing Chinese","enabled",true,"sortOrder",0),Map.of("value","en-US","label","English","enabled",true,"sortOrder",1)))));
        }
        var app=new SpringApplication(OperationsApplication.class);app.setRegisterShutdownHook(false);
        context=app.run("--server.port=0","--spring.datasource.url="+url,"--spring.datasource.username=sa","--spring.datasource.password=",
            "--OPS_ADMIN_USERNAME=test-operator","--OPS_ADMIN_PASSWORD="+PASSWORD,"--logging.level.root=WARN");
        base="http://127.0.0.1:"+context.getEnvironment().getProperty("local.server.port");
        jdbc=new JdbcTemplate(context.getBean(javax.sql.DataSource.class));configs=context.getBean(BrowserConfiguration.class);
        auth=context.getBean(ConfigOperatorAuth.class);operator=(ConfigOperator)auth.login(Map.of("username","test-operator","password",PASSWORD)).get("user");
    }
    @AfterAll void stop() { if(context!=null)context.close(); }
    private HttpResponse<String> call(String method,String path,String token,Object data) {
        try {
            var request=HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/json");
            if(token!=null)request.header("Authorization","Bearer "+token);
            request.method(method,data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json(data)));
            return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private Map<String,Object> body(HttpResponse<String> response,int status) { assertEquals(status,response.statusCode(),response.body());return object(response.body()); }
    private String token() { return text(body(call("POST","/api/admin/auth/login",null,Map.of("username","test-operator","password",PASSWORD)),200),"token"); }
    @SuppressWarnings("unchecked") private Map<String,Long> itemCounts(String token) {
        var response=call("GET","/api/admin/configs",token,null);assertEquals(200,response.statusCode(),response.body());
        List<Map<String,Object>> summaries=JSON.readValue(response.body(),List.class);var counts=new LinkedHashMap<String,Long>();
        for(var summary:summaries)counts.put(text(summary,"key"),number(summary,"itemCount"));return counts;
    }
    private Map<String,Object> edit(String key) { return object(json(configs.get(operator,key))); }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> entries(Map<String,Object> document) { return (List<Map<String,Object>>)document.get("entries"); }
    private void status(int expected,Runnable task) { assertEquals(expected,assertThrows(BusinessException.class,task::run).status); }

    @Test @SuppressWarnings("unchecked") void proxyBindingsAreAuthorizedAtomicVersionedBalancedAndPublicOverHttp() {
        jdbc.update("INSERT INTO tenant(id,name) VALUES('bind-tenant','Binding tenant'),('bind-other','Other tenant')");
        jdbc.update("INSERT INTO relay_pool(id,code,name,candidate_limit) VALUES('bind-pool','bind-pool','Binding group',1)");
        jdbc.update("INSERT INTO relay_pool_tenant(pool_id,tenant_id) VALUES('bind-pool','bind-tenant')");
        var nodeIds=new ArrayList<String>();
        for(int i=0;i<9;i++) {
            String id="binding-node-"+i;nodeIds.add(id);
            jdbc.update("INSERT INTO relay_node(id,pool_id,name,region,protocol,host,port,username,password,proxy_options,sort_order) VALUES(?,'bind-pool',?,'test','socks5',?,1080,'synthetic-username','synthetic-ciphertext','{}',?)",id,id,"binding-"+i+".example.test",i);
        }
        for(String id:List.of("binding-a","binding-b","binding-other","binding-dynamic"))
            jdbc.update("INSERT INTO device(id,tenant_id,name,ip_type,web_type,protocol,host,port,password) VALUES(?,?,?,2,?,'http','exit.example.test',8080,'private-exit-password')",id,id.equals("binding-other")?"bind-other":"bind-tenant",id,id.equals("binding-dynamic")?1:2);
        String token=token(),path="/api/admin/customer-proxies/binding-a/relays";
        assertEquals(401,call("GET",path,null,null).statusCode());assertEquals(401,call("GET",path,"tenant-session-token",null).statusCode());
        var initial=body(call("GET",path,token,null),200);assertEquals(1L,number(initial,"revision"));
        assertEquals(0,((List<?>)initial.get("items")).size());assertEquals(9,((List<?>)initial.get("available")).size());
        var saved=body(call("PUT",path,token,Map.of("revision",1,"nodeIds",List.of(nodeIds.get(0)))),200);
        assertEquals(2L,number(saved,"revision"));assertFalse(json(saved).contains("synthetic-ciphertext"));assertFalse(json(saved).contains("synthetic-username"));
        body(call("PUT",path,token,Map.of("revision",1,"nodeIds",List.of(nodeIds.get(1)))),409);
        body(call("PUT",path,token,Map.of("revision",2,"nodeIds",nodeIds)),400);
        body(call("PUT","/api/admin/customer-proxies/binding-dynamic/relays",token,Map.of("revision",1,"nodeIds",List.of(nodeIds.get(0)))),400);
        String batch="/api/admin/customer-proxies/relays/batch";
        body(call("POST",batch,token,Map.of("deviceIds",List.of("binding-a","binding-other"),"nodeIds",List.of(nodeIds.get(1)),"mode","replace")),409);
        assertEquals(nodeIds.get(0),jdbc.queryForObject("SELECT node_id FROM device_relay_node WHERE device_id='binding-a'",String.class));
        assertEquals(2L,jdbc.queryForObject("SELECT relay_binding_revision FROM device WHERE id='binding-a'",Long.class));
        body(call("POST",batch,token,Map.of("deviceIds",List.of("binding-a","binding-b"),"nodeIds",List.of(nodeIds.get(1)),"mode","replace","revisions",Map.of("binding-a",1,"binding-b",1))),409);
        var allocated=body(call("POST","/api/admin/customer-proxies/relays/allocate",token,Map.of("deviceIds",List.of("binding-a","binding-b"),"count",1)),200);
        var allocations=(List<Map<String,Object>>)allocated.get("items");assertEquals(2,allocations.size());
        assertNotEquals(allocations.get(0).get("nodeIds"),allocations.get(1).get("nodeIds"));
        long revision=jdbc.queryForObject("SELECT relay_binding_revision FROM device WHERE id='binding-a'",Long.class);
        // Explicit bindings may use eight nodes even when the historical group candidateLimit is one.
        saved=body(call("PUT",path,token,Map.of("revision",revision,"nodeIds",nodeIds.subList(0,8))),200);
        assertEquals(8,((List<?>)saved.get("items")).size());revision=number(saved,"revision");
        var customer=body(call("GET","/api/admin/customer-proxies/binding-a",token,null),200);
        assertEquals(8L,number(customer,"relayCount"));assertEquals(8L,number(customer,"relayAvailableCount"));assertFalse(json(customer).contains("private-exit-password"));
        var inverse=body(call("GET","/api/admin/relay-nodes/"+nodeIds.get(0)+"/bindings?page=1&size=1",token,null),200);
        assertEquals(1,((List<?>)inverse.get("items")).size());assertTrue(number(inverse,"total")>=1);assertFalse(json(inverse).contains("private-exit-password"));
        var flat=body(call("GET","/api/admin/relay-nodes?poolId=bind-pool&size=100",token,null),200);
        assertTrue(((List<Map<String,Object>>)flat.get("items")).stream().anyMatch(n->number(n,"boundProxyCount")>0));
        jdbc.update("UPDATE relay_pool_tenant SET enabled=FALSE WHERE pool_id='bind-pool'");
        var revoked=body(call("GET",path,token,null),200);assertEquals(8,((List<?>)revoked.get("items")).size());assertEquals(0,((List<?>)revoked.get("available")).size());
        assertTrue(((List<Map<String,Object>>)revoked.get("items")).stream().noneMatch(n->flag(n,"available")||flag(n,"authorized")));
        body(call("PUT",path,token,Map.of("revision",revision,"nodeIds",List.of(nodeIds.get(0)))),409);
        saved=body(call("PUT",path,token,Map.of("revision",revision,"nodeIds",List.of())),200);
        assertEquals(0,((List<?>)saved.get("items")).size());
        assertFalse(jdbc.queryForList("SELECT before_json,after_json FROM config_audit WHERE config_key='proxy-relay-bindings'").toString().contains("private-exit-password"));
        assertEquals(200,call("POST","/api/admin/auth/logout",token,Map.of()).statusCode());
    }

    @Test void adoptsExistingSharedSchemaWithoutChangingBusinessMigrationHistoryOrOverwritingData() {
        assertEquals(businessHistoryRows,jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history",Long.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name='ops_flyway_schema_history'",Integer.class));
        assertEquals(9,configs.list(operator).size());
        var before=edit("languages");long revision=number(before,"revision");
        assertTrue(revision>=7);configs.initialize();assertEquals(json(before),json(configs.get(operator,"languages")));
        assertEquals("existing-site",entries(edit("platform-sites")).get(0).get("id"));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM browser_config WHERE config_key IN ('network-routing','network-checks','access-policy','passkey-rules')",Integer.class));
    }
    @Test void customerAndAnonymousCredentialsCannotAccessOperationsOrTenantEndpoints() {
        assertEquals(401,call("GET","/api/admin/configs",null,null).statusCode());
        assertEquals(401,call("GET","/api/admin/configs","tenant-session-token",null).statusCode());
        String token=token();
        assertEquals("test-operator",body(call("GET","/api/admin/auth/me",token,null),200).get("username"));
        var counts=itemCounts(token);assertEquals(0L,counts.get("access-policy"));assertEquals(0L,counts.get("passkey-rules"));
        assertEquals(6L,counts.get("network-checks"));assertEquals(3L,counts.get("browser-defaults"));
        assertEquals(403,call("GET","/api/browser-config",token,null).statusCode());
        assertEquals(403,call("GET","/api/me/permissions",token,null).statusCode());
        assertEquals("operations",body(call("GET","/api/health",null,null),200).get("service"));
        assertEquals(200,call("POST","/api/admin/auth/logout",token,Map.of()).statusCode());
        assertEquals(401,call("GET","/api/admin/auth/me",token,null).statusCode());
    }
    @Test void publishedConfigurationUsesOptimisticRevisionAndIsVisibleThroughSharedTables() {
        String token=token();var before=body(call("GET","/api/admin/configs/languages",token,null),200);
        long revision=number(before,"revision"),total=number(configs.audit(operator,1,20),"total");
        entries(before).get(0).put("label","Published shared value");
        var saved=body(call("PUT","/api/admin/configs/languages",token,before),200);
        assertEquals(revision+1,number(saved,"revision"));
        assertTrue(jdbc.queryForObject("SELECT payload FROM browser_config WHERE config_key='languages'",String.class).contains("Published shared value"));
        body(call("PUT","/api/admin/configs/languages",token,before),409);
        assertEquals(total+1,number(configs.audit(operator,1,20),"total"));
    }
    @Test void localeModesPublishThroughOperationsHttpAndRemainValidAsSharedDefaults() {
        String token=text(auth.login(Map.of("username","test-operator","password",PASSWORD)),"token");
        var originals=new LinkedHashMap<String,Map<String,Object>>();
        for(String key:List.of("languages","timezones","browser-defaults"))originals.put(key,body(call("GET","/api/admin/configs/"+key,token,null),200));
        try {
            var languages=object(json(originals.get("languages")));
            if(entries(languages).stream().noneMatch(e->text(e,"value").equals("auto")))
                entries(languages).add(new LinkedHashMap<>(Map.of("value","auto","label","Follow proxy IP","enabled",true,"sortOrder",10000)));
            entries(languages).stream().filter(e->text(e,"value").equals("auto")).findFirst().orElseThrow().put("label","IP language");
            var savedLanguages=body(call("PUT","/api/admin/configs/languages",token,languages),200);
            assertEquals(number(languages,"revision")+1,number(savedLanguages,"revision"));
            assertTrue(entries(object(jdbc.queryForObject("SELECT payload FROM browser_config WHERE config_key='languages'",String.class))).stream()
                .anyMatch(e->text(e,"value").equals("auto")&&text(e,"label").equals("IP language")&&flag(e,"enabled")));

            var timezones=object(json(originals.get("timezones")));
            for(String value:List.of("auto","__local__")) {
                if(entries(timezones).stream().noneMatch(e->text(e,"value").equals(value)))
                    entries(timezones).add(new LinkedHashMap<>(Map.of("value",value,"label",value,"enabled",true,"sortOrder",10000)));
                var entry=entries(timezones).stream().filter(e->text(e,"value").equals(value)).findFirst().orElseThrow();
                entry.put("enabled",true);entry.put("label",value.equals("auto")?"IP timezone":"Local timezone");
            }
            var savedZones=body(call("PUT","/api/admin/configs/timezones",token,timezones),200);
            assertEquals(number(timezones,"revision")+1,number(savedZones,"revision"));
            var storedZones=entries(object(jdbc.queryForObject("SELECT payload FROM browser_config WHERE config_key='timezones'",String.class)));
            for(String value:List.of("auto","__local__"))assertTrue(storedZones.stream().anyMatch(e->text(e,"value").equals(value)&&flag(e,"enabled")));

            var defaults=object(json(originals.get("browser-defaults")));var automatic=Map.of("timezone","auto","language","auto","resolution","1920x1080");
            defaults.put("values",automatic);var savedDefaults=body(call("PUT","/api/admin/configs/browser-defaults",token,defaults),200);
            assertEquals(automatic,savedDefaults.get("values"));assertEquals(automatic,object(jdbc.queryForObject("SELECT payload FROM browser_config WHERE config_key='browser-defaults'",String.class)).get("values"));
            assertEquals(automatic,body(call("GET","/api/admin/configs/browser-defaults",token,null),200).get("values"));

            long audits=number(configs.audit(operator,1,20),"total"),revision=number(configs.runtime(),"revision");
            var disabled=edit("languages");entries(disabled).stream().filter(e->text(e,"value").equals("auto")).findFirst().orElseThrow().put("enabled",false);
            body(call("PUT","/api/admin/configs/languages",token,disabled),400);
            var invalid=edit("languages");entries(invalid).add(Map.of("value","__local__","label","Invalid language sentinel","enabled",true,"sortOrder",10001));
            body(call("PUT","/api/admin/configs/languages",token,invalid),400);
            assertEquals(audits,number(configs.audit(operator,1,20),"total"));assertEquals(revision,number(configs.runtime(),"revision"));

            defaults=edit("browser-defaults");var local=Map.of("timezone","__local__","language","auto","resolution","1920x1080");defaults.put("values",local);
            assertEquals(local,body(call("PUT","/api/admin/configs/browser-defaults",token,defaults),200).get("values"));
            assertEquals(local,object(jdbc.queryForObject("SELECT payload FROM browser_config WHERE config_key='browser-defaults'",String.class)).get("values"));
            String before=json(configs.get(operator,"browser-defaults"));configs.initialize();assertEquals(before,json(configs.get(operator,"browser-defaults")));
        } finally {
            // This suite deliberately shares one synthetic database; restore initial defaults before its dictionaries.
            for(String key:List.of("browser-defaults","languages","timezones")) {
                var restore=edit(key);String field=key.equals("browser-defaults")?"values":"entries";
                restore.put(field,originals.get(key).get(field));configs.save(operator,key,restore);
            }
        }
    }
    @Test void missingDictionarySeedsIncludeLocaleModesWithoutOverwritingExistingOperationalDefaults() {
        var old=jdbc.queryForMap("SELECT payload,revision,updated_at FROM browser_config WHERE config_key='languages'");
        String defaults=json(edit("browser-defaults"));
        try {
            jdbc.update("DELETE FROM browser_config WHERE config_key='languages'");configs.initialize();
            assertTrue(entries(edit("languages")).stream().anyMatch(e->text(e,"value").equals("auto")&&flag(e,"enabled")));
            for(String value:List.of("auto","__local__"))assertTrue(entries(edit("timezones")).stream().anyMatch(e->text(e,"value").equals(value)&&flag(e,"enabled")));
            assertEquals(defaults,json(edit("browser-defaults")));
            var before=edit("languages");configs.initialize();assertEquals(json(before),json(edit("languages")));
        } finally {
            jdbc.update("UPDATE browser_config SET payload=?,revision=?,updated_at=? WHERE config_key='languages'",old.get("payload"),old.get("revision"),old.get("updated_at"));
        }
    }
    @Test void invalidDefaultAndDictionaryRulesCannotPublishOrWriteAudit() {
        long total=number(configs.audit(operator,1,20),"total");
        var timezones=edit("timezones");entries(timezones).stream().filter(e->text(e,"value").equals("Asia/Shanghai")).findFirst().orElseThrow().put("enabled",false);
        status(400,()->configs.save(operator,"timezones",timezones));
        var defaults=edit("browser-defaults");defaults.put("values",Map.of("timezone","Unknown/Zone","language","en-US","resolution","1920x1080"));
        status(400,()->configs.save(operator,"browser-defaults",defaults));
        for(String key:List.of("timezones","languages","resolutions")) {
            var invalid=edit(key);entries(invalid).get(0).put("value","not a valid value");status(400,()->configs.save(operator,key,invalid));
        }
        var malformed=edit("languages");malformed.put("entries",List.of(5));status(400,()->configs.save(operator,"languages",malformed));
        assertEquals(total,number(configs.audit(operator,1,20),"total"));
        String token=token();body(call("GET","/api/admin/configs/unsupported-capability",token,null),404);
        body(call("GET","/api/admin/config-audit?page=0",token,null),400);
    }
    @Test void platformIdsAndDisabledHistoricalEntriesArePreserved() {
        var doc=edit("platform-sites");entries(doc).add(Map.of("id","","categoryName","Shopping","platformName","Shopee","siteName","MY","url","https://seller.shopee.com.my/","enabled",true,"sortOrder",1));
        var saved=configs.save(operator,"platform-sites",doc);assertFalse(text(entries(saved).get(1),"id").isBlank());
        var disabled=edit("platform-sites");entries(disabled).get(0).put("enabled",false);configs.save(operator,"platform-sites",disabled);
        assertEquals("existing-site",entries(edit("platform-sites")).get(0).get("id"));
        var removed=edit("platform-sites");entries(removed).remove(0);status(400,()->configs.save(operator,"platform-sites",removed));
        var bad=edit("platform-sites");entries(bad).get(0).put("url","javascript:alert(1)");status(400,()->configs.save(operator,"platform-sites",bad));
    }
    @Test void auditFailureRollsBackDocumentAndBothRevisions() {
        var before=edit("resolutions");long global=jdbc.queryForObject("SELECT revision FROM config_state WHERE id=1",Long.class),total=number(configs.audit(operator,1,20),"total");
        jdbc.execute("ALTER TABLE config_audit ADD CONSTRAINT reject_resolution CHECK(config_key <> 'resolutions')");
        try {
            var request=object(json(before));entries(request).get(0).put("label","Must roll back");
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->configs.save(operator,"resolutions",request));
            assertEquals(json(before),json(configs.get(operator,"resolutions")));
            assertEquals(global,jdbc.queryForObject("SELECT revision FROM config_state WHERE id=1",Long.class));
            assertEquals(total,number(configs.audit(operator,1,20),"total"));
        }finally{jdbc.execute("ALTER TABLE config_audit DROP CONSTRAINT reject_resolution");}
    }
    @Test void concurrentPublishersCannotOverwriteEachOther() throws Exception {
        var first=edit("languages");var second=edit("languages");entries(first).get(0).put("label","First publisher");entries(second).get(0).put("label","Second publisher");
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Integer> a=()->{start.await();try{configs.save(operator,"languages",first);return 200;}catch(BusinessException e){return e.status;}};
            Callable<Integer> b=()->{start.await();try{configs.save(operator,"languages",second);return 200;}catch(BusinessException e){return e.status;}};
            var fa=executor.submit(a);var fb=executor.submit(b);start.countDown();
            assertEquals(Set.of(200,409),Set.of(fa.get(15,TimeUnit.SECONDS),fb.get(15,TimeUnit.SECONDS)));
        }finally{executor.shutdownNow();}
    }
    @Test void bootstrapNeverResetsExistingCredentialsAndSessionsAreHashedAndRevocable() {
        auth.bootstrap("different-operator","Different-password-123!");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM config_operator",Integer.class));
        status(401,()->auth.login(Map.of("username","different-operator","password","Different-password-123!")));
        String token=text(auth.login(Map.of("username","test-operator","password",PASSWORD)),"token");
        assertFalse(jdbc.queryForList("SELECT token_hash FROM config_operator_session",String.class).contains(token));
        assertEquals(operator,auth.authenticate(token));auth.logout(token);status(401,()->auth.authenticate(token));
    }
    @Test void acceptsAnExistingBusinessServiceSessionAndHonorsSharedRevocation() throws Exception {
        String legacyToken="ops_existing-business-service-session";
        String tokenHash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(legacyToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("INSERT INTO config_operator_session(token_hash,operator_id,expires_at) VALUES(?,?,?)",tokenHash,operator.id(),java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(600)));
        try {
            assertEquals(200,call("GET","/api/admin/configs",legacyToken,null).statusCode());
            jdbc.update("UPDATE config_operator SET enabled=FALSE WHERE id=?",operator.id());
            assertEquals(401,call("GET","/api/admin/configs",legacyToken,null).statusCode());
            jdbc.update("UPDATE config_operator SET enabled=TRUE WHERE id=?",operator.id());
            jdbc.update("DELETE FROM config_operator_session WHERE token_hash=?",tokenHash);
            assertEquals(401,call("GET","/api/admin/configs",legacyToken,null).statusCode());
        } finally {
            jdbc.update("UPDATE config_operator SET enabled=TRUE WHERE id=?",operator.id());
            jdbc.update("DELETE FROM config_operator_session WHERE token_hash=?",tokenHash);
        }
    }
    @Test void anEmptyDatabaseFailsBeforeAnyFlywayHistoryOrApplicationTableIsWritten() {
        try(var source=new HikariDataSource()) {
            source.setJdbcUrl("jdbc:h2:mem:empty-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");source.setUsername("sa");source.setPassword("");
            assertThrows(IllegalStateException.class,()->new SharedSchema(source).verify());
            assertEquals(0,new JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public'",Integer.class));
        }
    }
    private String customer(String suffix) {
        String userId="runtime-"+suffix,token="existing-customer-session-"+suffix;
        jdbc.update("INSERT INTO app_user(id,tenant_id) VALUES(?,?)",userId,"existing-tenant");
        jdbc.update("INSERT INTO auth_session(token_hash,user_id,expires_at) VALUES(?,?,?)",hash(token),userId,java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(600)));
        return token;
    }
    private static String hash(String token) {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    @Test void runtimeUsesExistingCustomerSessionButNeverGrantsOperatorAccess() {
        String customer=customer("roles");
        var response=call("GET","/api/runtime/config",customer,null);var snapshot=body(response,200);
        assertEquals(Set.of("schemaVersion","revision","networkRouting","accessPolicy","networkChecks","passkeyRules"),snapshot.keySet());
        assertEquals(1,number(snapshot,"schemaVersion"));assertTrue(number(snapshot,"revision")>=1);
        assertEquals("no-store",response.headers().firstValue("cache-control").orElse(""));
        assertEquals(401,call("GET","/api/runtime/config",null,null).statusCode());
        assertEquals(401,call("GET","/api/runtime/config",token(),null).statusCode());
        assertEquals(401,call("GET","/api/admin/configs",customer,null).statusCode());
        assertEquals(401,call("PUT","/api/admin/configs/network-routing",customer,Map.of()).statusCode());
        assertEquals(403,call("PUT","/api/runtime/config",customer,Map.of()).statusCode());
    }
    @Test void runtimeRejectsExpiredRevokedDisabledAndDeletedBusinessSessions() {
        String customer=customer("revocation"),userId="runtime-revocation";
        assertEquals(200,call("GET","/api/runtime/config",customer,null).statusCode());
        jdbc.update("UPDATE app_user SET enabled=FALSE WHERE id=?",userId);
        assertEquals(401,call("GET","/api/runtime/config",customer,null).statusCode());
        jdbc.update("UPDATE app_user SET enabled=TRUE,deleted=TRUE WHERE id=?",userId);
        assertEquals(401,call("GET","/api/runtime/config",customer,null).statusCode());
        jdbc.update("UPDATE app_user SET deleted=FALSE WHERE id=?",userId);
        jdbc.update("UPDATE auth_session SET expires_at=? WHERE token_hash=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),hash(customer));
        assertEquals(401,call("GET","/api/runtime/config",customer,null).statusCode());
        jdbc.update("DELETE FROM auth_session WHERE token_hash=?",hash(customer));
        assertEquals(401,call("GET","/api/runtime/config",customer,null).statusCode());
    }
    @Test void publishedRuntimeConfigurationIsImmediatelyVisibleAndPreservedByInitialization() {
        String customer=customer("publish");var doc=edit("network-routing");
        var values=new LinkedHashMap<String,Object>(RuntimeRules.defaults("network-routing"));values.put("enabled",true);values.put("directDomains",List.of("*.amazon.com"));doc.put("values",values);
        long revision=number(configs.runtime(),"revision");long audits=number(configs.audit(operator,1,20),"total");
        var saved=configs.save(operator,"network-routing",doc);
        var read=body(call("GET","/api/runtime/config",customer,null),200);
        assertEquals(values,read.get("networkRouting"));assertEquals(revision+1,number(read,"revision"));
        assertEquals(1L,itemCounts(text(auth.login(Map.of("username","test-operator","password",PASSWORD)),"token")).get("network-routing"));
        configs.initialize();assertEquals(json(saved),json(configs.get(operator,"network-routing")));
        status(409,()->configs.save(operator,"network-routing",doc));
        assertEquals(audits+1,number(configs.audit(operator,1,20),"total"));
    }
    @Test void runtimeRuleErrorsCannotChangeThePublishedDocumentOrAudit() {
        String token=token();var before=body(call("GET","/api/admin/configs/network-checks",token,null),200);
        long audits=number(configs.audit(operator,1,20),"total"),revision=number(configs.runtime(),"revision");
        var invalid=object(json(before));invalid.put("values",Map.of("enabled",true,"proxyProviders",List.of("https://127.0.0.1/"),"localProviders",List.of("https://ipwho.is/")));
        body(call("PUT","/api/admin/configs/network-checks",token,invalid),400);
        assertEquals(json(before),json(configs.get(operator,"network-checks")));
        assertEquals(audits,number(configs.audit(operator,1,20),"total"));assertEquals(revision,number(configs.runtime(),"revision"));
    }
}
