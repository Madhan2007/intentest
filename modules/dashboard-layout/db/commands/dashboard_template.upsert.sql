INSERT INTO dashboard_template (template_key, app_key, template_name, description, thumbnail_uri, is_builtin, created_by)
VALUES (:template_key, :app_key, :template_name, :description, :thumbnail_uri, :is_builtin, :created_by::uuid)
ON CONFLICT (template_key) DO UPDATE SET
  app_key = EXCLUDED.app_key,
  template_name = EXCLUDED.template_name,
  description = EXCLUDED.description,
  thumbnail_uri = EXCLUDED.thumbnail_uri,
  is_builtin = EXCLUDED.is_builtin;
