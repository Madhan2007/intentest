-- Inserts a platform admin only when no account has already claimed the username.
INSERT INTO platform_admin (id, username, display_name, password_hash, enabled)
VALUES (:id::uuid, :username, :display_name, :password_hash, :enabled)
ON CONFLICT (username) DO NOTHING;
