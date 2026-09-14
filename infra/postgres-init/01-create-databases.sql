-- Provisions the additional named databases used by multi-database routing
-- (platform.yaml's databases.OPZUSER/OPZMAIN/OPZHUB). Applied two ways:
--   1. postgres /docker-entrypoint-initdb.d on a genuinely empty data volume
--   2. the postgres-ensure compose service on every `up` (covers existing
--      volumes and other machines that already have pg-data)
-- Idempotent — CREATE DATABASE is skipped when the name already exists.
SELECT 'CREATE DATABASE opzuser OWNER erp_app'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'opzuser')\gexec

SELECT 'CREATE DATABASE opzmain OWNER erp_app'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'opzmain')\gexec

SELECT 'CREATE DATABASE opzhub OWNER erp_app'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'opzhub')\gexec

GRANT ALL PRIVILEGES ON DATABASE opzuser TO erp_app;
GRANT ALL PRIVILEGES ON DATABASE opzmain TO erp_app;
GRANT ALL PRIVILEGES ON DATABASE opzhub TO erp_app;
