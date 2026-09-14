-- Inserts a user only when no account has already claimed the username.
INSERT INTO id_user (id, username, display_name, password_hash, roles, enabled)
VALUES (:id::uuid, :username, :display_name, :password_hash, ARRAY[:role]::TEXT[], :enabled)
ON CONFLICT (username) DO NOTHING;