-- Test-only equivalent of business V26; operations never runs Flyway in production.
ALTER TABLE relay_node ADD CONSTRAINT relay_node_protocol_supported CHECK(protocol IN ('http','https','socks5','ss'));
ALTER TABLE relay_node DROP CONSTRAINT relay_node_legacy_protocol;
