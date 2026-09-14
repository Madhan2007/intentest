SELECT id, company_id, template_name, subject, body_html, body_text, created_at, updated_at
FROM mkt_email_template
WHERE company_id = :company_id::uuid
ORDER BY template_name ASC;
