INSERT INTO dashboard_layout (id, company_id, app_key, role_key, template_key, layout_data, updated_by, updated_at)
VALUES (:id::uuid, :company_id::uuid, :app_key, :role_key, :template_key, :layout_data::jsonb, :updated_by::uuid, NOW())
ON CONFLICT (company_id, app_key, role_key) DO UPDATE SET
  template_key = EXCLUDED.template_key,
  layout_data = EXCLUDED.layout_data,
  updated_by = EXCLUDED.updated_by,
  updated_at = NOW();
