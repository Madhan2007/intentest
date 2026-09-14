DELETE FROM dashboard_layout_user
WHERE user_id = :user_id::uuid AND company_id = :company_id::uuid AND app_key = :app_key;
