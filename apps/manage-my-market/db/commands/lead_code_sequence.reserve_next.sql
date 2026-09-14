INSERT INTO mkt_lead_code_sequence (id, company_id, prefix, next_number, created_at)
VALUES (gen_random_uuid(), :company_id::uuid, :prefix, 2, now())
ON CONFLICT (company_id, prefix)
DO UPDATE SET next_number = mkt_lead_code_sequence.next_number + 1
RETURNING (mkt_lead_code_sequence.next_number - 1) AS reserved_number;
