-- Assigns the bootstrap administrator email when that username exists without it.
UPDATE id_user
SET email = :email
WHERE username = :username
  AND email IS DISTINCT FROM :email
  AND NOT EXISTS (
    SELECT 1
    FROM id_user existing_user
    WHERE existing_user.email = :email
      AND existing_user.username <> :username
  );
