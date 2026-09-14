SELECT id, template_key, widget_key, zone_key, position, widget_props
FROM dashboard_template_widget
WHERE template_key = :template_key;
