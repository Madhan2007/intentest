SELECT template_key, app_key, template_name, description, thumbnail_uri, is_builtin, created_by, created_at
FROM dashboard_template
WHERE template_key = :template_key;
