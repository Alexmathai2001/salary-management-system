package com.acme.salary.insights;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * All aggregation happens in SQL so it stays fast at 10k+ rows and nothing is shipped to the client.
 *
 * <p>Every query starts from the same {@code pay} CTE, which converts each salary to USD cents:
 * {@code salary_minor / minor_unit_scale / (rate_micro / 1e6) * 100}. It is an INNER JOIN on the FX table, which is
 * safe because employees cannot be created in a currency without a rate.
 */
@Repository
public class InsightsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public InsightsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Stats grouped by a dimension, or a single overall row (key "ALL") when {@code dimension} is null. */
    public List<PayStats> stats(Dimension dimension, InsightsFilter filter) {
        Map<String, Object> params = new HashMap<>();
        String groupExpr = dimension == null ? "'ALL'" : "p." + dimension.column();
        String sql = payCte(filter, params) + """
                , ranked AS (
                    SELECT %s AS grp, p.usd_cents,
                           ROW_NUMBER() OVER (PARTITION BY %s ORDER BY p.usd_cents) AS rn,
                           COUNT(*)     OVER (PARTITION BY %s)                      AS cnt
                    FROM pay p
                )
                SELECT grp,
                       COUNT(*)        AS headcount,
                       SUM(usd_cents)  AS total,
                       AVG(usd_cents)  AS avg,
                       MIN(usd_cents)  AS min,
                       MAX(usd_cents)  AS max,
                       AVG(CASE WHEN rn IN ((cnt + 1) / 2, (cnt + 2) / 2) THEN usd_cents END) AS median,
                       MIN(CASE WHEN rn >= (cnt * 25 + 99) / 100 THEN usd_cents END)          AS p25,
                       MIN(CASE WHEN rn >= (cnt * 75 + 99) / 100 THEN usd_cents END)          AS p75
                FROM ranked
                GROUP BY grp
                ORDER BY grp
                """.formatted(groupExpr, groupExpr, groupExpr);
        return jdbc.query(sql, params, (rs, i) -> new PayStats(
                rs.getString("grp"),
                rs.getLong("headcount"),
                Math.round(rs.getDouble("total")),
                Math.round(rs.getDouble("avg")),
                Math.round(rs.getDouble("median")),
                Math.round(rs.getDouble("p25")),
                Math.round(rs.getDouble("p75")),
                Math.round(rs.getDouble("min")),
                Math.round(rs.getDouble("max"))));
    }

    /** Histogram in USD with a fixed bucket width. Empty buckets between the extremes are included as zeros. */
    public List<DistributionBucket> distribution(InsightsFilter filter, long bucketWidthUsd) {
        Map<String, Object> params = new HashMap<>();
        params.put("widthCents", bucketWidthUsd * 100);
        String sql = payCte(filter, params) + """
                SELECT CAST(p.usd_cents / :widthCents AS INTEGER) AS bucket, COUNT(*) AS n
                FROM pay p
                GROUP BY bucket
                ORDER BY bucket
                """;
        TreeMap<Long, Long> counts = new TreeMap<>();
        jdbc.query(sql, params, rs -> {
            counts.put(rs.getLong("bucket"), rs.getLong("n"));
        });
        List<DistributionBucket> result = new ArrayList<>();
        if (counts.isEmpty()) {
            return result;
        }
        for (long b = counts.firstKey(); b <= counts.lastKey(); b++) {
            result.add(new DistributionBucket(b * bucketWidthUsd, (b + 1) * bucketWidthUsd, counts.getOrDefault(b, 0L)));
        }
        return result;
    }

    /** Highest (or lowest) paid employees by USD-equivalent pay. Ties break on id for stable output. */
    public List<EarnerResponse> earners(InsightsFilter filter, boolean top, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("limit", limit);
        String sql = payCte(filter, params) + """
                SELECT p.id, p.employee_code, p.first_name, p.last_name, p.country, p.department, p.job_title,
                       p.job_level, p.currency, p.salary_minor, p.usd_cents
                FROM pay p
                ORDER BY p.usd_cents %s, p.id
                LIMIT :limit
                """.formatted(top ? "DESC" : "ASC");
        return jdbc.query(sql, params, (rs, i) -> new EarnerResponse(
                rs.getLong("id"), rs.getString("employee_code"),
                rs.getString("first_name") + " " + rs.getString("last_name"),
                rs.getString("country"), rs.getString("department"), rs.getString("job_title"),
                rs.getString("job_level"), rs.getString("currency"), rs.getLong("salary_minor"),
                Math.round(rs.getDouble("usd_cents"))));
    }

    /** Builds the "WITH pay AS (...)" prefix, adding only the filters that are set (all values are bound). */
    private static String payCte(InsightsFilter f, Map<String, Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (!f.includeInactive()) {
            where.append(" AND e.status = 'ACTIVE'");
        }
        addEquals(where, params, "country", "e.country", f.country() == null ? null : f.country().toUpperCase());
        addEquals(where, params, "department", "e.department", f.department());
        addEquals(where, params, "jobLevel", "e.job_level", f.jobLevel());
        addEquals(where, params, "jobTitle", "e.job_title", f.jobTitle());
        return """
                WITH pay AS (
                    SELECT e.id, e.employee_code, e.first_name, e.last_name, e.country, e.department,
                           e.job_title, e.job_level, e.currency, e.salary_minor,
                           CAST(e.salary_minor AS REAL) * 100.0 * 1000000.0
                               / (f.rate_micro * f.minor_unit_scale) AS usd_cents
                    FROM employees e
                    JOIN fx_rates f ON f.currency = e.currency
                """ + where + ")\n";
    }

    private static void addEquals(StringBuilder where, Map<String, Object> params, String name, String column,
            String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" = :").append(name);
            params.put(name, value.trim());
        }
    }
}
