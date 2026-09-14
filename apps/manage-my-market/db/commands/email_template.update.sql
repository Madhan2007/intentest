UPDATE mkt_email_template
SET template_name = :template_name,
    subject = :subject,
    body_html = :body_html,
    body_text = :body_text,
    updated_at = now()
WHERE id = :id::uuid AND company_id = :company_id::uuid;
