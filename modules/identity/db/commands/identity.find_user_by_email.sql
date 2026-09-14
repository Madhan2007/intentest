-- Finds an identity row by stored email only.
SELECT id, username, email, display_name, password_hash, roles, enabled, company_id
FROM id_user
WHERE email = :email
