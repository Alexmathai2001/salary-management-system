package com.acme.salary.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.salary.insights.Outlier.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests of the outlier rule. Every group has hand-computed median / MAD so expectations can be checked on
 * paper. Amounts are in USD cents (thousands of dollars x 100,000).
 */
class OutlierDetectorTest {

    static final OutlierParams DEFAULTS = OutlierParams.defaults();
    final AtomicLong ids = new AtomicLong();

    /** Peers share country, job title and level; amounts are given in thousands of USD. */
    private List<EarnerResponse> group(String country, String title, String level, double... thousandsUsd) {
        List<EarnerResponse> list = new ArrayList<>();
        for (double k : thousandsUsd) {
            list.add(person(country, title, level, Math.round(k * 100_000)));
        }
        return list;
    }

    private EarnerResponse person(String country, String title, String level, long usdCents) {
        long id = ids.incrementAndGet();
        return new EarnerResponse(id, "EMP-" + id, "Person " + id, country, "Engineering", title, level, "USD",
                usdCents, usdCents);
    }

    @SafeVarargs
    private static List<EarnerResponse> combine(List<EarnerResponse>... groups) {
        List<EarnerResponse> all = new ArrayList<>();
        for (var g : groups) {
            all.addAll(g);
        }
        return all;
    }

    @Test
    void flagsAClearHighOutlier() {
        // median 100k, MAD 1k, so 200k has modified z = 0.6745 * 100 / 1 = 67.45 and deviation +100%
        var pay = group("US", "Engineer", "L3", 98, 100, 101, 99, 102, 100, 200);

        OutlierReport report = OutlierDetector.detect(pay, DEFAULTS);

        assertThat(report.outliers()).hasSize(1);
        Outlier o = report.outliers().get(0);
        assertThat(o.direction()).isEqualTo(Direction.HIGH);
        assertThat(o.usdCents()).isEqualTo(20_000_000L);
        assertThat(o.peerMedianCents()).isEqualTo(10_000_000L);
        assertThat(o.peerCount()).isEqualTo(7);
        assertThat(o.deviationPct()).isEqualTo(100.0);
        assertThat(o.modifiedZ()).isEqualTo(67.45);
    }

    @Test
    void flagsAClearLowOutlier() {
        var pay = group("US", "Engineer", "L3", 98, 99, 100, 100, 101, 102, 40);

        Outlier o = OutlierDetector.detect(pay, DEFAULTS).outliers().get(0);

        assertThat(o.direction()).isEqualTo(Direction.LOW);
        assertThat(o.deviationPct()).isEqualTo(-60.0);
        assertThat(o.modifiedZ()).isEqualTo(-40.47);
    }

    @Test
    void doesNotFlagOrdinaryVariation() {
        // median 100k, MAD 5k: the largest gap (110k) has z = 1.35, well under 3.5
        var pay = group("US", "Engineer", "L3", 90, 95, 100, 105, 110);

        assertThat(OutlierDetector.detect(pay, DEFAULTS).outliers()).isEmpty();
    }

    @Test
    void doesNotJudgeGroupsBelowTheMinimumSizeAndSaysSo() {
        var pay = group("US", "Engineer", "L3", 100, 101, 99, 500); // 4 peers, one wildly different

        OutlierReport report = OutlierDetector.detect(pay, DEFAULTS);

        assertThat(report.outliers()).isEmpty();
        assertThat(report.employeesNotEvaluated()).isEqualTo(4);
        assertThat(report.employeesEvaluated()).isZero();
        assertThat(report.groupsEvaluated()).isZero();
    }

    @Test
    void aGroupAtExactlyTheMinimumSizeIsEvaluated() {
        var pay = group("US", "Engineer", "L3", 100, 100, 101, 99, 300);

        OutlierReport report = OutlierDetector.detect(pay, DEFAULTS);

        assertThat(report.employeesEvaluated()).isEqualTo(5);
        assertThat(report.outliers()).hasSize(1);
    }

    @Test
    void peersAreComparedOnlyWithinTheirOwnCountryTitleAndLevel() {
        // Pooled, every IN salary would look like a low outlier next to the US ones.
        var us = group("US", "Engineer", "L3", 100, 101, 99, 100, 102);
        var in = group("IN", "Engineer", "L3", 25, 26, 24, 25, 25.5);
        var usSenior = group("US", "Engineer", "L5", 180, 179, 181, 180, 182);

        OutlierReport report = OutlierDetector.detect(combine(us, in, usSenior), DEFAULTS);

        assertThat(report.outliers()).isEmpty();
        assertThat(report.groupsEvaluated()).isEqualTo(3);
        assertThat(report.employeesEvaluated()).isEqualTo(15);
    }

    @Test
    void whenPeersAreAllPaidTheSameAnyBigGapIsFlaggedByPercentageAlone() {
        // median 100k, MAD 0 (z undefined). 130k is +30%: flagged. 105k is +5%: not.
        var pay = group("US", "Engineer", "L3", 100, 100, 100, 100, 100, 105, 130);

        OutlierReport report = OutlierDetector.detect(pay, DEFAULTS);

        assertThat(report.outliers()).hasSize(1);
        assertThat(report.outliers().get(0).usdCents()).isEqualTo(13_000_000L);
        assertThat(report.outliers().get(0).modifiedZ()).isNull();
        assertThat(report.outliers().get(0).deviationPct()).isEqualTo(30.0);
    }

    @Test
    void aStatisticallyLargeButPracticallyTinyGapIsNotFlagged() {
        // Very tight group (MAD 0.01k) so 110k has a huge z, but it is only ~10% above the median: below the 20% bar.
        var pay = new ArrayList<EarnerResponse>();
        for (long cents : new long[] {10_000_000, 10_010_000, 10_020_000, 10_030_000, 11_000_000}) {
            pay.add(person("US", "Engineer", "L3", cents));
        }

        assertThat(OutlierDetector.detect(pay, DEFAULTS).outliers()).isEmpty();
    }

    @Test
    void usesTheAverageOfTheTwoMiddleValuesForAnEvenGroup() {
        // median (104+106)/2 = 105k; |dev| = 5,3,1,1,3,195 so MAD = (3+3)/2 = 3k; 300k: z = 0.6745*195/3 = 43.84
        var pay = group("US", "Engineer", "L3", 100, 102, 104, 106, 108, 300);

        Outlier o = OutlierDetector.detect(pay, DEFAULTS).outliers().get(0);

        assertThat(o.peerMedianCents()).isEqualTo(10_500_000L);
        assertThat(o.deviationPct()).isEqualTo(185.7);
        assertThat(o.modifiedZ()).isEqualTo(43.84);
    }

    @Test
    void ranksTheBiggestDeviationFirstAcrossGroups() {
        var lowGroup = group("IN", "Analyst", "L2", 100, 100, 101, 99, 100, 60);   // -40%
        var highGroup = group("US", "Engineer", "L3", 98, 100, 101, 99, 102, 100, 200); // +100%

        var outliers = OutlierDetector.detect(combine(lowGroup, highGroup), DEFAULTS).outliers();

        assertThat(outliers).extracting(Outlier::direction).containsExactly(Direction.HIGH, Direction.LOW);
        assertThat(outliers).extracting(Outlier::deviationPct).containsExactly(100.0, -40.0);
    }

    @Test
    void stricterParametersFlagFewerPeople() {
        var pay = group("US", "Engineer", "L3", 100, 101, 99, 100, 102, 135); // +35%

        assertThat(OutlierDetector.detect(pay, DEFAULTS).outliers()).hasSize(1);
        assertThat(OutlierDetector.detect(pay, new OutlierParams(5, 3.5, 50)).outliers()).isEmpty();
        assertThat(OutlierDetector.detect(pay, new OutlierParams(7, 3.5, 20)).outliers()).isEmpty(); // group now too small
    }

    @Test
    void emptyInputGivesAnEmptyReport() {
        OutlierReport report = OutlierDetector.detect(List.of(), DEFAULTS);

        assertThat(report.outliers()).isEmpty();
        assertThat(report.employeesEvaluated()).isZero();
        assertThat(report.employeesNotEvaluated()).isZero();
    }

    @Test
    void rejectsNonsensicalParameters() {
        assertThatThrownBy(() -> new OutlierParams(2, 3.5, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutlierParams(5, 0.5, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutlierParams(5, 3.5, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutlierParams(5, 3.5, 500)).isInstanceOf(IllegalArgumentException.class);
    }
}
