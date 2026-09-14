INSERT INTO mkt_email_template (
    id, company_id, template_name, subject, body_html, body_text, created_at, updated_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :company_id::uuid,
    :template_name,
    :subject,
    :body_html,
    :body_text,
    now(),
    now()
);
