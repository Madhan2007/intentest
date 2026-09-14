INSERT INTO widget_catalog (widget_key, app_key, widget_name, description, icon_key, default_zone,
                            default_w, default_h, min_w, min_h, props_schema, allowed_roles, is_system, status)
VALUES (:widget_key, :app_key, :widget_name, :description, :icon_key, :default_zone,
        :default_w, :default_h, :min_w, :min_h, :props_schema::jsonb, :allowed_roles::text[], :is_system, :status)
ON CONFLICT (widget_key) DO UPDATE SET
  app_key = EXCLUDED.app_key,
  widget_name = EXCLUDED.widget_name,
  description = EXCLUDED.description,
  icon_key = EXCLUDED.icon_key,
  default_zone = EXCLUDED.default_zone,
  default_w = EXCLUDED.default_w,
  default_h = EXCLUDED.default_h,
  min_w = EXCLUDED.min_w,
  min_h = EXCLUDED.min_h,
  props_schema = EXCLUDED.props_schema,
  allowed_roles = EXCLUDED.allowed_roles,
  is_system = EXCLUDED.is_system,
  status = EXCLUDED.status;
