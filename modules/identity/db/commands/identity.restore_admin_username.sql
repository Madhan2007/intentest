-- Splits a legacy email-as-username administrator into username plus email.
UPDATE id_user
SET email = :email,
    username = :username
WHERE username = :email
  AND NOT EXISTS (
    SELECT 1
    FROM id_user existing_user
    WHERE existing_user.username = :username
  );
