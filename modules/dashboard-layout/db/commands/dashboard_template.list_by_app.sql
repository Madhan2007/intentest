SELECT template_key, app_key, template_name, description, thumbnail_uri, is_builtin, created_by, created_at
FROM dashboard_template
WHERE app_key = :app_key OR app_key = '*'
ORDER BY template_name;
