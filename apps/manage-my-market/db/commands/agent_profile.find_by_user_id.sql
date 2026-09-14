SELECT agent_user_id, company_id, marketing_type, linked_person_id, created_at, updated_at
FROM mkt_agent_profile
WHERE agent_user_id = :agent_user_id::uuid;
