/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.Referrer;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DataClient-backed repository for Referrer operations.
 */
public class DataClientReferrerRepository implements ReferrerRepository {

    private final DataClient dataClient;

    public DataClientReferrerRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void insert(Referrer referrer) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", referrer.id());
        params.put("company_id", referrer.companyId());
        params.put("referrer_name", referrer.referrerName());
        params.put("email", referrer.email());
        params.put("phone", referrer.phone());
        params.put("status", referrer.status());
        params.put("joined_at", referrer.joinedAt() != null ? Date.valueOf(referrer.joinedAt()) : null);

        dataClient.execute(MarketDataConstants.REFERRER_INSERT, params);
    }

    @Override
    public void update(Referrer referrer) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", referrer.id());
        params.put("company_id", referrer.companyId());
        params.put("referrer_name", referrer.referrerName());
        params.put("email", referrer.email());
        params.put("phone", referrer.phone());
        params.put("status", referrer.status());

        dataClient.execute(MarketDataConstants.REFERRER_UPDATE, params);
    }

    @Override
    public List<Referrer> listByCompany(String companyId, String status) {
        Map<String, Object> params = new HashMap<>();
        params.put("company_id", companyId);
        params.put("status", status);

        return dataClient.query(MarketDataConstants.REFERRER_LIST_BY_COMPANY, params).stream()
            .map(DataClientReferrerRepository::referrerFromRow)
            .toList();
    }

    @Override
    public Optional<Referrer> findById(String id, String companyId) {
        return listByCompany(companyId, null).stream().filter(r -> r.id().equals(id)).findFirst();
    }

    @Override
    public void recomputeAggregates(String referrerId) {
        dataClient.execute(MarketDataConstants.REFERRER_RECOMPUTE_AGGREGATES, Map.of("referrer_id", referrerId));
    }

    private static Referrer referrerFromRow(Row row) {
        Object lc = row.get("leads_count");
        int leadsCount = lc instanceof Number n ? n.intValue() : 0;

        LocalDate joinedAt = null;
        Object jdo = row.get("joined_at");
        if (jdo instanceof Date d) {
            joinedAt = d.toLocalDate();
        } else if (jdo instanceof LocalDate ld) {
            joinedAt = ld;
        } else if (jdo != null) {
            joinedAt = LocalDate.parse(jdo.toString());
        }

        return new Referrer(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("company_id") != null ? row.get("company_id").toString() : null,
            row.getString("referrer_name"),
            row.getString("email"),
            row.getString("phone"),
            row.getString("status"),
            leadsCount,
            row.getDecimal("rewards_paid_total"),
            row.getDecimal("rewards_pending_total"),
            joinedAt,
            row.getInstant("created_at"),
            row.getInstant("updated_at")
        );
    }
}
