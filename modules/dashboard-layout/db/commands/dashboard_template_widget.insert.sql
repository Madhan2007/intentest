INSERT INTO dashboard_template_widget (id, template_key, widget_key, zone_key, position, widget_props)
VALUES (:id::uuid, :template_key, :widget_key, :zone_key, :position::jsonb, :widget_props::jsonb);
