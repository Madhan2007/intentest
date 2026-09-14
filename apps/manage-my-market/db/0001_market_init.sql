-- ============================================================================
-- ManageMyOpz — Manage My Market Database Initialization
-- Logical Database: OPZMARKET (with identity/form seeds on OPZUSER)
-- ============================================================================

-- 1. mkt_lead_code_sequence
CREATE TABLE IF NOT EXISTS mkt_lead_code_sequence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    prefix TEXT NOT NULL DEFAULT '',
    next_number BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_mkt_lead_code_sequence_company_prefix
    ON mkt_lead_code_sequence (company_id, prefix);

-- 2. mkt_referrer
CREATE TABLE IF NOT EXISTS mkt_referrer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    referrer_name TEXT NOT NULL,
    email TEXT,
    phone TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    leads_count INT NOT NULL DEFAULT 0,
    rewards_paid_total NUMERIC NOT NULL DEFAULT 0,
    rewards_pending_total NUMERIC NOT NULL DEFAULT 0,
    joined_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_referrer_company_status
    ON mkt_referrer (company_id, status);

-- 3. mkt_lead
CREATE TABLE IF NOT EXISTS mkt_lead (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    lead_code TEXT NOT NULL,
    lead_source_type TEXT NOT NULL,
    first_name TEXT,
    last_name TEXT,
    display_name TEXT NOT NULL,
    company_name TEXT,
    email TEXT,
    phone TEXT,
    status TEXT NOT NULL DEFAULT 'NEW',
    source_detail TEXT,
    estimated_value INT,
    priority TEXT,
    intent_score INT,
    referrer_id UUID REFERENCES mkt_referrer(id) ON DELETE RESTRICT,
    owner_user_id UUID,
    created_by_user_id UUID,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_mkt_lead_company_code
    ON mkt_lead (company_id, lead_code);
CREATE INDEX IF NOT EXISTS idx_mkt_lead_company_status
    ON mkt_lead (company_id, status);
CREATE INDEX IF NOT EXISTS idx_mkt_lead_company_source_type
    ON mkt_lead (company_id, lead_source_type);
CREATE INDEX IF NOT EXISTS idx_mkt_lead_owner
    ON mkt_lead (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_mkt_lead_referrer
    ON mkt_lead (referrer_id);

-- 4. mkt_lead_assignment
CREATE TABLE IF NOT EXISTS mkt_lead_assignment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id UUID NOT NULL REFERENCES mkt_lead(id) ON DELETE CASCADE,
    assigned_by_user_id UUID,
    assigned_to_user_id UUID,
    transfer_reason TEXT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_lead_assignment_lead_assigned
    ON mkt_lead_assignment (lead_id, assigned_at);

-- 5. mkt_lead_score
CREATE TABLE IF NOT EXISTS mkt_lead_score (
    lead_id UUID PRIMARY KEY REFERENCES mkt_lead(id) ON DELETE CASCADE,
    score INT NOT NULL DEFAULT 0,
    breakdown_json JSONB,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 6. mkt_lead_followup
CREATE TABLE IF NOT EXISTS mkt_lead_followup (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id UUID NOT NULL REFERENCES mkt_lead(id) ON DELETE CASCADE,
    reason TEXT,
    scheduled_at TIMESTAMPTZ NOT NULL,
    assigned_agent_user_id UUID,
    priority TEXT,
    status TEXT NOT NULL DEFAULT 'SCHEDULED',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_lead_followup_lead
    ON mkt_lead_followup (lead_id);
CREATE INDEX IF NOT EXISTS idx_mkt_lead_followup_agent_status
    ON mkt_lead_followup (assigned_agent_user_id, status);

-- 7. mkt_call_queue
CREATE TABLE IF NOT EXISTS mkt_call_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    queue_name TEXT NOT NULL,
    description TEXT,
    priority INT NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_call_queue_company_status
    ON mkt_call_queue (company_id, status);

-- 8. mkt_call_queue_item
CREATE TABLE IF NOT EXISTS mkt_call_queue_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_id UUID NOT NULL REFERENCES mkt_call_queue(id) ON DELETE CASCADE,
    lead_id UUID NOT NULL REFERENCES mkt_lead(id) ON DELETE RESTRICT,
    priority TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    assigned_agent_user_id UUID,
    call_attempts INT NOT NULL DEFAULT 0,
    last_dialed_at TIMESTAMPTZ,
    next_call_scheduled_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_call_queue_item_queue_status
    ON mkt_call_queue_item (queue_id, status);
CREATE INDEX IF NOT EXISTS idx_mkt_call_queue_item_lead
    ON mkt_call_queue_item (lead_id);
CREATE INDEX IF NOT EXISTS idx_mkt_call_queue_item_agent
    ON mkt_call_queue_item (assigned_agent_user_id);

-- 9. mkt_call_log
CREATE TABLE IF NOT EXISTS mkt_call_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    queue_id UUID REFERENCES mkt_call_queue(id) ON DELETE RESTRICT,
    lead_id UUID REFERENCES mkt_lead(id) ON DELETE RESTRICT,
    agent_user_id UUID,
    phone_number TEXT NOT NULL,
    connection_status TEXT NOT NULL,
    disposition TEXT,
    duration_seconds INT NOT NULL DEFAULT 0,
    recording_url TEXT,
    notes TEXT,
    scheduled_followup_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_call_log_company_created
    ON mkt_call_log (company_id, created_at);
CREATE INDEX IF NOT EXISTS idx_mkt_call_log_lead
    ON mkt_call_log (lead_id);
CREATE INDEX IF NOT EXISTS idx_mkt_call_log_agent
    ON mkt_call_log (agent_user_id);

-- 10. mkt_lead_activity
CREATE TABLE IF NOT EXISTS mkt_lead_activity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id UUID NOT NULL REFERENCES mkt_lead(id) ON DELETE CASCADE,
    activity_type TEXT NOT NULL DEFAULT 'NOTE',
    title TEXT NOT NULL,
    description TEXT,
    old_value TEXT,
    new_value TEXT,
    related_call_log_id UUID REFERENCES mkt_call_log(id) ON DELETE RESTRICT,
    related_followup_id UUID REFERENCES mkt_lead_followup(id) ON DELETE RESTRICT,
    performed_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_lead_activity_lead_created
    ON mkt_lead_activity (lead_id, created_at);

-- 11. mkt_campaign
CREATE TABLE IF NOT EXISTS mkt_campaign (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    campaign_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    start_date DATE,
    end_date DATE,
    budget NUMERIC,
    created_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_campaign_company_status
    ON mkt_campaign (company_id, status);

-- 12. mkt_campaign_channel
CREATE TABLE IF NOT EXISTS mkt_campaign_channel (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES mkt_campaign(id) ON DELETE CASCADE,
    channel_type TEXT NOT NULL,
    budget NUMERIC NOT NULL DEFAULT 0,
    spend NUMERIC NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_campaign_channel_campaign
    ON mkt_campaign_channel (campaign_id);

-- 13. mkt_campaign_recipient
CREATE TABLE IF NOT EXISTS mkt_campaign_recipient (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES mkt_campaign(id) ON DELETE CASCADE,
    lead_id UUID NOT NULL REFERENCES mkt_lead(id) ON DELETE RESTRICT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_mkt_campaign_recipient_campaign_lead
    ON mkt_campaign_recipient (campaign_id, lead_id);
CREATE INDEX IF NOT EXISTS idx_mkt_campaign_recipient_lead
    ON mkt_campaign_recipient (lead_id);

-- 14. mkt_email_template
CREATE TABLE IF NOT EXISTS mkt_email_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    template_name TEXT NOT NULL,
    subject TEXT NOT NULL,
    body_html TEXT,
    body_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_email_template_company_name
    ON mkt_email_template (company_id, template_name);

-- 15. mkt_audience
CREATE TABLE IF NOT EXISTS mkt_audience (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    audience_name TEXT NOT NULL,
    description TEXT,
    rules_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_audience_company_name
    ON mkt_audience (company_id, audience_name);

-- 16. mkt_journey
CREATE TABLE IF NOT EXISTS mkt_journey (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    journey_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    trigger_type TEXT NOT NULL,
    flow_definition_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_journey_company_status
    ON mkt_journey (company_id, status);

-- 17. mkt_journey_enrollment
CREATE TABLE IF NOT EXISTS mkt_journey_enrollment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journey_id UUID NOT NULL REFERENCES mkt_journey(id) ON DELETE CASCADE,
    lead_id UUID NOT NULL REFERENCES mkt_lead(id) ON DELETE RESTRICT,
    current_node_id TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_journey_enrollment_journey_lead
    ON mkt_journey_enrollment (journey_id, lead_id);
CREATE INDEX IF NOT EXISTS idx_mkt_journey_enrollment_status
    ON mkt_journey_enrollment (status);

-- 18. mkt_referral_reward
CREATE TABLE IF NOT EXISTS mkt_referral_reward (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referrer_id UUID NOT NULL REFERENCES mkt_referrer(id) ON DELETE CASCADE,
    lead_id UUID REFERENCES mkt_lead(id) ON DELETE RESTRICT,
    amount NUMERIC NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    payout_details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_referral_reward_referrer_status
    ON mkt_referral_reward (referrer_id, status);

-- 19. mkt_agent_profile
CREATE TABLE IF NOT EXISTS mkt_agent_profile (
    agent_user_id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    marketing_type TEXT NOT NULL,
    linked_person_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mkt_agent_profile_company_type
    ON mkt_agent_profile (company_id, marketing_type);
CREATE INDEX IF NOT EXISTS idx_mkt_agent_profile_linked_person
    ON mkt_agent_profile (linked_person_id);

-- ============================================================================
-- RBAC Seed Data (Runs against OPZUSER database)
-- ============================================================================

INSERT INTO id_role (role_code, role_title, is_system) VALUES
  ('market_admin',       'Marketing Administrator', true),
  ('market_manager',     'Campaign Manager',        true),
  ('market_telecaller',  'Telemarketing Agent',     true),
  ('market_field_agent', 'Field Marketing Agent',   true)
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO id_role_permission (role_code, module_id, feature_id, permissions) VALUES
  ('market_admin',       'manage-my-market', 'led', 'vcud'),
  ('market_admin',       'manage-my-market', 'cmp', 'vcud'),
  ('market_admin',       'manage-my-market', 'jny', 'vcud'),
  ('market_admin',       'manage-my-market', 'cal', 'vcud'),
  ('market_admin',       'manage-my-market', 'ref', 'vcud'),
  ('market_admin',       'manage-my-market', 'agt', 'vcud'),
  ('market_manager',     'manage-my-market', 'led', 'vcu'),
  ('market_manager',     'manage-my-market', 'cmp', 'vcu'),
  ('market_manager',     'manage-my-market', 'jny', 'vcu'),
  ('market_manager',     'manage-my-market', 'cal', 'v'),
  ('market_manager',     'manage-my-market', 'ref', 'v'),
  ('market_telecaller',  'manage-my-market', 'led', 'vu'),
  ('market_telecaller',  'manage-my-market', 'cal', 'vcu'),
  ('market_telecaller',  'manage-my-market', 'agt', 'v'),
  ('market_field_agent', 'manage-my-market', 'led', 'vu'),
  ('market_field_agent', 'manage-my-market', 'agt', 'v')
ON CONFLICT (role_code, module_id, feature_id) DO UPDATE SET permissions = EXCLUDED.permissions;

-- ============================================================================
-- Form Field Definitions Seed Data (Runs against OPZUSER database)
-- ============================================================================

INSERT INTO id_field_definition (
  form_id, field_key, field_heading, field_type, is_mandatory, display_order, allowed_values, subactions
) VALUES
  ('manage-my-market.lead.create', 'lead_source_type', 'Source Type', 'select', true, 1,
   '[{"value":"MANUAL","label":"Manual"},{"value":"INBOX","label":"Inbox"},{"value":"REFERRAL","label":"Referral"},{"value":"CAMPAIGN","label":"Campaign"}]'::jsonb,
   '[{"action":"lock_after_create","trigger":"submit"}]'::jsonb),
  ('manage-my-market.lead.create', 'display_name', 'Name', 'text', true, 2, NULL, NULL),
  ('manage-my-market.lead.create', 'company_name', 'Company', 'text', false, 3, NULL, NULL),
  ('manage-my-market.lead.create', 'email', 'Email', 'email', false, 4, NULL, NULL),
  ('manage-my-market.lead.create', 'phone', 'Phone', 'phone', false, 5, NULL, NULL),
  ('manage-my-market.lead.create', 'priority', 'Priority', 'select', false, 6,
   '[{"value":"HOT","label":"Hot"},{"value":"WARM","label":"Warm"},{"value":"COLD","label":"Cold"}]'::jsonb, NULL),
  ('manage-my-market.lead.create', 'referrer_id', 'Referrer', 'lookup', false, 7,
   '{"res":"manage-my-market.referrer"}'::jsonb,
   '[{"action":"visible_when","field":"lead_source_type","equals":"REFERRAL"}]'::jsonb),

  ('manage-my-market.lead.edit', 'display_name', 'Name', 'text', true, 1, NULL, NULL),
  ('manage-my-market.lead.edit', 'company_name', 'Company', 'text', false, 2, NULL, NULL),
  ('manage-my-market.lead.edit', 'email', 'Email', 'email', false, 3, NULL, NULL),
  ('manage-my-market.lead.edit', 'phone', 'Phone', 'phone', false, 4, NULL, NULL),
  ('manage-my-market.lead.edit', 'status', 'Status', 'select', true, 5,
   '[{"value":"NEW","label":"New"},{"value":"CONTACTED","label":"Contacted"},{"value":"QUALIFIED","label":"Qualified"},{"value":"CONVERTED","label":"Converted"},{"value":"LOST","label":"Lost"},{"value":"DISQUALIFIED","label":"Disqualified"}]'::jsonb, NULL),
  ('manage-my-market.lead.edit', 'priority', 'Priority', 'select', false, 6,
   '[{"value":"HOT","label":"Hot"},{"value":"WARM","label":"Warm"},{"value":"COLD","label":"Cold"}]'::jsonb, NULL),
  ('manage-my-market.lead.edit', 'owner_user_id', 'Owner', 'lookup', false, 7, '{"res":"identity.user"}'::jsonb, NULL),

  ('manage-my-market.call-queue-item.edit', 'status', 'Status', 'select', true, 1,
   '[{"value":"PENDING","label":"Pending"},{"value":"IN_PROGRESS","label":"In Progress"},{"value":"COMPLETED","label":"Completed"},{"value":"SKIPPED","label":"Skipped"}]'::jsonb, NULL),
  ('manage-my-market.call-queue-item.edit', 'priority', 'Priority', 'select', false, 2,
   '[{"value":"HOT","label":"Hot"},{"value":"WARM","label":"Warm"},{"value":"COLD","label":"Cold"}]'::jsonb, NULL),
  ('manage-my-market.call-queue-item.edit', 'next_call_scheduled_at', 'Next Call', 'datetime', false, 3, NULL, NULL)
ON CONFLICT (form_id, field_key) DO UPDATE
SET field_heading = EXCLUDED.field_heading,
    field_type = EXCLUDED.field_type,
    is_mandatory = EXCLUDED.is_mandatory,
    display_order = EXCLUDED.display_order,
    allowed_values = EXCLUDED.allowed_values,
    subactions = EXCLUDED.subactions;
