-- Inserts a user only when no account has already claimed the username.
INSERT INTO id_user (id, username, email, display_name, password_hash, roles, enabled, company_id)
VALUES (:id::uuid, :username, :email, :display_name, :password_hash, ARRAY[:role]::TEXT[], :enabled, :company_id::uuid)
ON CONFLICT (username) DO NOTHING;
