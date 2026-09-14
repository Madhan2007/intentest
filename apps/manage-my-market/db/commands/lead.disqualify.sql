UPDATE mkt_lead
SET status = 'DISQUALIFIED',
    notes = CASE WHEN :reason IS NOT NULL AND :reason <> '' THEN COALESCE(notes || E'\n', '') || 'Disqualified: ' || :reason ELSE notes END,
    updated_at = now()
WHERE id = :id::uuid AND company_id = :company_id::uuid;
