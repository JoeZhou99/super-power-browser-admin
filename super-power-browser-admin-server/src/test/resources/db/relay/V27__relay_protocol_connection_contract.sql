-- Test-only equivalent of business V27; keep V26's old expression to exercise upgrading it.
ALTER TABLE relay_node ADD CONSTRAINT relay_node_protocol_v27 CHECK(CASE protocol WHEN 'http' THEN 1 WHEN 'https' THEN 1 WHEN 'socks5' THEN 1 WHEN 'ss' THEN 1 ELSE 0 END=1);
ALTER TABLE relay_node DROP CONSTRAINT relay_node_protocol_supported;
