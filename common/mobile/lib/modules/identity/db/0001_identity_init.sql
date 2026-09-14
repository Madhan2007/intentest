# Kernel identity tables — applied when db.type=postgres (doc 07 §3.4, doc 19).
-- Composite (tenant_id, id) omitted for this first pass (single-tenant site profile).
-- SECURITY NOTE: See SECURITY.md and doc 18 for user creation workflow.

CREATE TABLE IF NOT EXISTS id_user (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        TEXT NOT NULL UNIQUE,
    display_name    TEXT NOT NULL,
    password_hash   TEXT NOT NULL,
    roles           TEXT[] NOT NULL DEFAULT '{}',
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ
);

