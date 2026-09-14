INSERT INTO dashboard_layout_user (id, user_id, company_id, app_key, layout_data, updated_at)
VALUES (:id::uuid, :user_id::uuid, :company_id::uuid, :app_key, :layout_data::jsonb, NOW())
ON CONFLICT (user_id, company_id, app_key) DO UPDATE SET
  layout_data = EXCLUDED.layout_data,
  updated_at = NOW();
