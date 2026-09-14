SELECT id, company_id, app_key, role_key, template_key, layout_data, updated_by, updated_at
FROM dashboard_layout
WHERE company_id = :company_id::uuid AND app_key = :app_key AND role_key = :role_key;
