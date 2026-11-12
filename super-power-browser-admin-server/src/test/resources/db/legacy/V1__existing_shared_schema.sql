-- Public platform/site snapshot: 2026-11-06.
-- Source: GET https://api.zhanfubrowser.com/api/v1/siteInfo/getSiteTreeByCategoriesV2
-- Excludes custom platforms, the duplicate All category, old TikTok global login entries,
-- and the Line URL that requires a vendor-specific browser extension.
CREATE TABLE platform_site (
 id VARCHAR(50) PRIMARY KEY,
 category_name VARCHAR(100) NOT NULL,
 platform_name VARCHAR(100) NOT NULL,
 site_name VARCHAR(200) NOT NULL,
 url VARCHAR(2000) NOT NULL,
 sort_order INT NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX platform_site_order ON platform_site(enabled,sort_order);

-- Platform configuration is deliberately separate from tenant/company administration.
CREATE TABLE config_operator (
 id VARCHAR(36) PRIMARY KEY,
 username VARCHAR(80) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE config_operator_session (
 token_hash VARCHAR(64) PRIMARY KEY,
 operator_id VARCHAR(36) NOT NULL,
 expires_at TIMESTAMP NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (operator_id) REFERENCES config_operator(id)
);
CREATE INDEX config_operator_session_expiry ON config_operator_session(expires_at);
CREATE TABLE config_state (
 id INT PRIMARY KEY,
 revision BIGINT NOT NULL DEFAULT 1
);
INSERT INTO config_state(id,revision) VALUES(1,1);
CREATE TABLE browser_config (
 config_key VARCHAR(40) PRIMARY KEY,
 payload LONGTEXT NOT NULL,
 revision BIGINT NOT NULL DEFAULT 1,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE config_audit (
 id VARCHAR(36) PRIMARY KEY,
 config_key VARCHAR(40) NOT NULL,
 operator_id VARCHAR(36) NOT NULL,
 operator_name VARCHAR(80) NOT NULL,
 revision BIGINT NOT NULL,
 before_json LONGTEXT NOT NULL,
 after_json LONGTEXT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (operator_id) REFERENCES config_operator(id)
);
CREATE INDEX config_audit_created ON config_audit(created_at);

INSERT INTO platform_site(id,category_name,platform_name,site_name,url,sort_order) VALUES ('existing-site','Shopping','Amazon','US','https://sellercentral.amazon.com/',0);

-- Relevant columns already owned by the business schema; never created by the operations service.
CREATE TABLE app_user (
 id VARCHAR(36) PRIMARY KEY,
 tenant_id VARCHAR(36) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE auth_session (
 token_hash VARCHAR(64) PRIMARY KEY,
 user_id VARCHAR(36) NOT NULL,
 expires_at TIMESTAMP NOT NULL,
 FOREIGN KEY (user_id) REFERENCES app_user(id)
);
