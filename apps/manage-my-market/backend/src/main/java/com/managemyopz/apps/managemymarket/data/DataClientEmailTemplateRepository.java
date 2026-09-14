/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.EmailTemplate;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DataClient-backed repository for Email template operations.
 */
public class DataClientEmailTemplateRepository implements EmailTemplateRepository {

    private final DataClient dataClient;

    public DataClientEmailTemplateRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void insert(EmailTemplate template) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", template.id());
        params.put("company_id", template.companyId());
        params.put("template_name", template.templateName());
        params.put("subject", template.subject());
        params.put("body_html", template.bodyHtml());
        params.put("body_text", template.bodyText());

        dataClient.execute(MarketDataConstants.EMAIL_TEMPLATE_INSERT, params);
    }

    @Override
    public void update(EmailTemplate template) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", template.id());
        params.put("company_id", template.companyId());
        params.put("template_name", template.templateName());
        params.put("subject", template.subject());
        params.put("body_html", template.bodyHtml());
        params.put("body_text", template.bodyText());

        dataClient.execute(MarketDataConstants.EMAIL_TEMPLATE_UPDATE, params);
    }

    @Override
    public List<EmailTemplate> listByCompany(String companyId) {
        return dataClient.query(MarketDataConstants.EMAIL_TEMPLATE_LIST_BY_COMPANY, Map.of("company_id", companyId)).stream()
            .map(DataClientEmailTemplateRepository::templateFromRow)
            .toList();
    }

    @Override
    public Optional<EmailTemplate> findById(String id, String companyId) {
        return listByCompany(companyId).stream().filter(t -> t.id().equals(id)).findFirst();
    }

    private static EmailTemplate templateFromRow(Row row) {
        return new EmailTemplate(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("company_id") != null ? row.get("company_id").toString() : null,
            row.getString("template_name"),
            row.getString("subject"),
            row.getString("body_html"),
            row.getString("body_text"),
            row.getInstant("created_at"),
            row.getInstant("updated_at")
        );
    }
}
