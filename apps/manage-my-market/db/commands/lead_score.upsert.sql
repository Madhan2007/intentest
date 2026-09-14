INSERT INTO mkt_lead_score (lead_id, score, breakdown_json, updated_at)
VALUES (:lead_id::uuid, :score, :breakdown_json::jsonb, now())
ON CONFLICT (lead_id)
DO UPDATE SET score = EXCLUDED.score,
              breakdown_json = EXCLUDED.breakdown_json,
              updated_at = now();
