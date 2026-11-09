package com.acme.salary.insights;

import com.acme.salary.insights.Outlier.Direction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flags employees whose USD pay is unusually far from their peers (same job title, level and country).
 *
 * <p>Why median/MAD instead of mean and standard deviation: a sample of n values can never put a point further than
 * (n-1)/sqrt(n) standard deviations from its mean, which is only 1.79 for n=5. A "beyond 2 sigma" rule therefore cannot
 * flag anyone in the small peer groups HR data really has. The median and the median absolute deviation (MAD) are also
 * not distorted by the very outlier being searched for.
 *
 * <p>An employee is flagged when BOTH hold:
 * <ul>
 *   <li>|modified z| = 0.6745 * |pay - median| / MAD  is at least {@code zThreshold} (skipped if MAD is 0, i.e. peers
 *       are paid identically), and</li>
 *   <li>|pay - median| / median is at least {@code minDeviationPct}.</li>
 * </ul>
 * Pure logic: no database, so every edge case is unit-tested directly.
 */
public final class OutlierDetector {

    private static final double MAD_SCALE = 0.6745;

    private OutlierDetector() {
    }

    public static OutlierReport detect(List<EarnerResponse> pay, OutlierParams params) {
        Map<String, List<EarnerResponse>> groups = new LinkedHashMap<>();
        for (EarnerResponse p : pay) {
            groups.computeIfAbsent(peerKey(p), k -> new ArrayList<>()).add(p);
        }

        List<Outlier> flagged = new ArrayList<>();
        int evaluated = 0;
        int notEvaluated = 0;
        int groupsEvaluated = 0;
        for (List<EarnerResponse> group : groups.values()) {
            if (group.size() < params.minGroupSize()) {
                notEvaluated += group.size();
                continue;
            }
            groupsEvaluated++;
            evaluated += group.size();
            flagInGroup(group, params, flagged);
        }

        flagged.sort(Comparator.comparingDouble((Outlier o) -> Math.abs(o.deviationPct())).reversed()
                .thenComparingLong(Outlier::id));
        return new OutlierReport(evaluated, notEvaluated, groupsEvaluated, flagged.size(), params, flagged);
    }

    private static void flagInGroup(List<EarnerResponse> group, OutlierParams params, List<Outlier> out) {
        long[] values = group.stream().mapToLong(EarnerResponse::usdCents).toArray();
        double median = median(values);
        double[] absDev = Arrays.stream(values).mapToDouble(v -> Math.abs(v - median)).toArray();
        double mad = median(absDev);

        for (EarnerResponse p : group) {
            double diff = p.usdCents() - median;
            if (diff == 0 || median <= 0) {
                continue;
            }
            double deviationPct = diff / median * 100;
            if (Math.abs(deviationPct) < params.minDeviationPct()) {
                continue;
            }
            Double z = null;
            if (mad > 0) {
                z = MAD_SCALE * diff / mad;
                if (Math.abs(z) < params.zThreshold()) {
                    continue;
                }
            }
            out.add(new Outlier(p.id(), p.employeeCode(), p.name(), p.country(), p.department(), p.jobTitle(),
                    p.jobLevel(), p.currency(), p.salaryMinor(), p.usdCents(), Math.round(median), group.size(),
                    round(deviationPct, 1), z == null ? null : round(z, 2),
                    diff > 0 ? Direction.HIGH : Direction.LOW));
        }
    }

    static String peerKey(EarnerResponse p) {
        return p.country() + "|" + p.jobTitle() + "|" + p.jobLevel();
    }

    static double median(long[] values) {
        return median(Arrays.stream(values).asDoubleStream().toArray());
    }

    static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }

    private static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
}
