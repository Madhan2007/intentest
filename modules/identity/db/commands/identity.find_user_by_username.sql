-- Named command (doc 07 §3.1): resolved by CommandCatalog, executed only by PostgresDataServer.
SELECT id, username, email, display_name, password_hash, roles, enabled, company_id
FROM id_user
WHERE username = :username
