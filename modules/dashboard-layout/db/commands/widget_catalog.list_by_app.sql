SELECT widget_key, app_key, widget_name, description, icon_key, default_zone,
       default_w, default_h, min_w, min_h, props_schema, allowed_roles, is_system, status, created_at
FROM widget_catalog
WHERE (app_key = :app_key OR app_key = '*') AND status = 'active'
ORDER BY widget_name;
