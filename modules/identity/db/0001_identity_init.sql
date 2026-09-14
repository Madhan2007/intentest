# Kernel identity tables — applied when db.type=postgres (doc 07 §3.4, doc 19).
-- Canonical table layout is modules/identity/db/schema/id_user.yaml (OPZUSER).
-- This file documents the same shape for humans; SchemaReconciler applies the YAML.

CREATE TABLE IF NOT EXISTS id_user (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        TEXT NOT NULL UNIQUE,
    email           TEXT UNIQUE,
    display_name    TEXT NOT NULL,
    password_hash   TEXT NOT NULL,
    roles           TEXT[] NOT NULL DEFAULT '{}',
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ,
    company_id      UUID
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_id_user_company_username
    ON id_user (company_id, username);
