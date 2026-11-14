package com.superpower.operations.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Never let an accidental first start create metadata in an uninitialized business database. */
@Component
public class SharedSchema {
    private final DataSource source;
    public SharedSchema(DataSource source) { this.source=source; }
    public void verify() {
        try(var connection=source.getConnection()) { verify(connection); }
        catch(SQLException e) { throw new IllegalStateException("共享数据库尚未准备好，请先完成业务服务截至 V28 及内核版本表的数据库迁移，再启动运营服务",e); }
    }
    public static void verify(Connection connection) throws SQLException {
        var selects=List.of(
            "SELECT token_hash,user_id,expires_at FROM auth_session WHERE 1=0",
            "SELECT id,tenant_id,username,display_name,enabled,deleted FROM app_user WHERE 1=0",
            "SELECT id,username,password_hash,enabled,created_at FROM config_operator WHERE 1=0",
            "SELECT token_hash,operator_id,expires_at,created_at FROM config_operator_session WHERE 1=0",
            "SELECT config_key,payload,revision,updated_at FROM browser_config WHERE 1=0",
            "SELECT id,config_key,operator_id,operator_name,revision,before_json,after_json,created_at FROM config_audit WHERE 1=0",
            "SELECT id,category_name,platform_name,site_name,url,enabled,sort_order FROM platform_site WHERE 1=0",
            "SELECT id,name,code,remark,enabled,candidate_limit,revision,updated_at FROM relay_pool WHERE 1=0",
            "SELECT id,pool_id,name,region,protocol,host,port,username,password,proxy_options,enabled,sort_order,revision,updated_at FROM relay_node WHERE 1=0",
            "SELECT pool_id,tenant_id,enabled,expires_at FROM relay_pool_tenant WHERE 1=0",
            "SELECT device_id,node_id,sort_order,created_at FROM device_relay_node WHERE 1=0",
            "SELECT id,name FROM tenant WHERE 1=0",
            "SELECT id,name,tenant_id,created_by,ip_type,relay_binding_revision,host,port,protocol,username,CASE WHEN password<>'' THEN TRUE ELSE FALSE END AS has_password,region,provider,map_ip,map_port,web_type,expires_at,recycled,created_at,check_connected,check_ip,check_country_name,check_latency_ms,check_message,checked_at FROM device WHERE 1=0",
            "SELECT id,name,tenant_id,device_id,platform,owner_id,recycled,last_opened_at,created_at FROM environment WHERE 1=0",
            "SELECT id,version,platform,arch,full_version,sha256,archive_bytes,descriptor,storage_type,storage_key,state,recommended,revision,created_at,updated_at FROM kernel_release WHERE 1=0",
            "SELECT id FROM kernel_catalog_lock WHERE id=1");
        for(String sql:selects)try(var statement=connection.createStatement();var ignored=statement.executeQuery(sql)) { }
        var metadata=connection.getMetaData();
        String device=metadata.storesUpperCaseIdentifiers()?"DEVICE":"device";
        String obsolete=metadata.storesUpperCaseIdentifiers()?"RELAY_POOL_ID":"relay_pool_id";
        try(var columns=metadata.getColumns(connection.getCatalog(),null,device,obsolete)) {
            if(columns.next())throw new SQLException("Obsolete device.relay_pool_id: business migration V25 is required");
        }
        try(var statement=connection.createStatement();var result=statement.executeQuery("SELECT revision FROM config_state WHERE id=1")) {
            if(!result.next())throw new SQLException("Missing config_state singleton");
        }
        // V25 rejects SS; V26's H2 CHECK can fail after its migration pool closes.
        // Flyway quotes its lowercase table/columns even when H2 uses uppercase
        // identifiers. Inspect the successful business migration, never write it.
        String quote=metadata.getIdentifierQuoteString().trim();
        if(quote.isEmpty())throw new SQLException("Cannot quote the business migration history");
        String history=quote+"flyway_schema_history"+quote,version=quote+"version"+quote,success=quote+"success"+quote;
        try(var statement=connection.prepareStatement("SELECT "+success+" FROM "+history+" WHERE "+version+"=?")) {
            statement.setString(1,"28");
            try(var result=statement.executeQuery()) {
                if(!result.next()||!result.getBoolean(1))throw new SQLException("Successful business migration V28 is required for explicit proxy relay bindings");
            }
        }
    }
}
