INSERT INTO mkt_agent_profile (
    agent_user_id, company_id, marketing_type, linked_person_id, created_at, updated_at
) VALUES (
    :agent_user_id::uuid,
    :company_id::uuid,
    :marketing_type,
    :linked_person_id::uuid,
    now(),
    now()
)
ON CONFLICT (agent_user_id)
DO UPDATE SET marketing_type = EXCLUDED.marketing_type,
              linked_person_id = EXCLUDED.linked_person_id,
              updated_at = now();
