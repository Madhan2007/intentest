/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Opaque row: a map of typed values, never a JDBC ResultSet (doc 07 §2). */
public record Row(Map<String, Object> values) {

    public Object get(String key) { return values.get(key); }

    public String getString(String key) { return (String) values.get(key); }

    public Long getLong(String key) {
        Object v = values.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.valueOf(v.toString());
    }

    public Boolean getBoolean(String key) {
        Object v = values.get(key);
        return v == null ? null : (Boolean) v;
    }

    public BigDecimal getDecimal(String key) {
        Object v = values.get(key);
        if (v == null) return null;
        if (v instanceof BigDecimal d) return d;
        return new BigDecimal(v.toString());
    }

    public Instant getInstant(String key) {
        Object v = values.get(key);
        return v == null ? null : (Instant) v;
    }
}
