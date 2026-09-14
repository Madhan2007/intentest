-- Named command (doc 07 §3.1): resolved by CommandCatalog, executed only by PostgresDataServer.
-- Runs against OPZMAIN (platform_admin), not OPZUSER.
SELECT id, username, display_name, password_hash, enabled
FROM platform_admin
WHERE username = :username
