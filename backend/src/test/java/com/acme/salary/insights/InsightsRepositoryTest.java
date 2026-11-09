package com.acme.salary.insights;

import static com.acme.salary.employee.TestEmployees.anEmployee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.fx.FxConverter;
import com.acme.salary.fx.FxRateRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The aggregation SQL runs against real SQLite with hand-checkable data. Salaries are in USD cents unless noted,
 * so expected values can be worked out on paper.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InsightsRepositoryTest {

    @Autowired
    InsightsRepository insights;

    @Autowired
    EmployeeRepository employees;

    @Autowired
    FxRateRepository fxRates;

    @BeforeEach
    void empty() {
        employees.deleteAll();
    }

    private static long usd(long dollars) {
        return dollars * 100;
    }

    private Employee save(String country, String department, String level, String currency, long salaryMinor) {
        return employees.saveAndFlush(anEmployee().country(country).department(department).level(level)
                .currency(currency).salary(salaryMinor).build());
    }

    private void saveUsd(String country, String department, long... dollars) {
        for (long d : dollars) {
            save(country, department, "L3", "USD", usd(d));
        }
    }

    @Test
    void summaryOfAnOddHeadcount() {
        saveUsd("US", "Engineering", 50_000, 60_000, 70_000, 80_000, 100_000);

        PayStats s = insights.stats(null, InsightsFilter.none()).get(0);

        assertThat(s.headcount()).isEqualTo(5);
        assertThat(s.totalCents()).isEqualTo(usd(360_000));
        assertThat(s.avgCents()).isEqualTo(usd(72_000));
        assertThat(s.medianCents()).isEqualTo(usd(70_000)); // middle value
        assertThat(s.p25Cents()).isEqualTo(usd(60_000));    // nearest rank ceil(0.25*5)=2
        assertThat(s.p75Cents()).isEqualTo(usd(80_000));    // nearest rank ceil(0.75*5)=4
        assertThat(s.minCents()).isEqualTo(usd(50_000));
        assertThat(s.maxCents()).isEqualTo(usd(100_000));
    }

    @Test
    void medianOfAnEvenHeadcountAveragesTheTwoMiddleValues() {
        saveUsd("US", "Engineering", 50_000, 60_000, 70_000, 80_000, 100_000, 120_000);

        PayStats s = insights.stats(null, InsightsFilter.none()).get(0);

        assertThat(s.medianCents()).isEqualTo(usd(75_000)); // (70k + 80k) / 2
        assertThat(s.p25Cents()).isEqualTo(usd(60_000));    // ceil(0.25*6)=2
        assertThat(s.p75Cents()).isEqualTo(usd(100_000));   // ceil(0.75*6)=5
    }

    @Test
    void singleEmployeeGroupHasAllStatsEqual() {
        saveUsd("US", "Engineering", 90_000);

        PayStats s = insights.stats(null, InsightsFilter.none()).get(0);

        assertThat(List.of(s.avgCents(), s.medianCents(), s.p25Cents(), s.p75Cents(), s.minCents(), s.maxCents()))
                .containsOnly(usd(90_000));
    }

    @Test
    void convertsEveryCurrencyToUsdBeforeAggregating() {
        // Each of these is exactly USD 100,000 at the seeded rates.
        save("US", "Engineering", "L3", "USD", usd(100_000));
        save("IN", "Engineering", "L3", "INR", 830_000_000L);   // 8,300,000 INR (paise)
        save("JP", "Engineering", "L3", "JPY", 15_000_000L);    // 15,000,000 JPY (no minor unit)
        save("DE", "Engineering", "L3", "EUR", 9_200_000L);     // 92,000 EUR

        PayStats s = insights.stats(null, InsightsFilter.none()).get(0);

        assertThat(s.headcount()).isEqualTo(4);
        assertThat(s.minCents()).isEqualTo(usd(100_000));
        assertThat(s.maxCents()).isEqualTo(usd(100_000));
        assertThat(s.totalCents()).isEqualTo(usd(400_000));
    }

    @Test
    void sqlConversionAgreesWithTheJavaReferenceFormula() {
        Employee e = save("IN", "Engineering", "L3", "INR", 1_234_567_89L);
        var rate = fxRates.findById("INR").orElseThrow();

        long fromSql = insights.earners(InsightsFilter.none(), true, 1).get(0).usdCents();
        long fromJava = FxConverter.toUsdCents(e.getSalaryMinor(), rate.getRateMicro(), rate.getFractionDigits());

        // Same formula, different floating-point operation order: allow a 1 cent difference at a rounding boundary.
        assertThat(fromSql).isCloseTo(fromJava, within(1L));
    }

    @Test
    void groupsByDimensionOrderedByKey() {
        saveUsd("US", "Engineering", 100_000, 120_000);
        saveUsd("IN", "Sales", 40_000);
        saveUsd("DE", "Engineering", 80_000);

        List<PayStats> byCountry = insights.stats(Dimension.COUNTRY, InsightsFilter.none());
        List<PayStats> byDepartment = insights.stats(Dimension.DEPARTMENT, InsightsFilter.none());

        assertThat(byCountry).extracting(PayStats::key).containsExactly("DE", "IN", "US");
        assertThat(byCountry).extracting(PayStats::headcount).containsExactly(1L, 1L, 2L);
        assertThat(byCountry.get(2).avgCents()).isEqualTo(usd(110_000));
        assertThat(byDepartment).extracting(PayStats::key).containsExactly("Engineering", "Sales");
        assertThat(byDepartment.get(0).headcount()).isEqualTo(3);
    }

    @Test
    void excludesInactiveEmployeesUnlessAsked() {
        saveUsd("US", "Engineering", 100_000);
        employees.saveAndFlush(anEmployee().country("US").department("Engineering").currency("USD")
                .salary(usd(500_000)).status(EmployeeStatus.INACTIVE).build());

        var activeOnly = insights.stats(null, InsightsFilter.none()).get(0);
        var everyone = insights.stats(null, new InsightsFilter(null, null, null, null, true)).get(0);

        assertThat(activeOnly.headcount()).isEqualTo(1);
        assertThat(activeOnly.maxCents()).isEqualTo(usd(100_000));
        assertThat(everyone.headcount()).isEqualTo(2);
        assertThat(everyone.maxCents()).isEqualTo(usd(500_000));
    }

    @Test
    void appliesDrillDownFilters() {
        saveUsd("US", "Engineering", 100_000, 120_000);
        saveUsd("US", "Sales", 70_000);
        saveUsd("IN", "Engineering", 30_000);

        var usEngineering = new InsightsFilter("us", "Engineering", null, null, false);

        assertThat(insights.stats(null, usEngineering).get(0).headcount()).isEqualTo(2);
        assertThat(insights.stats(null, new InsightsFilter("IN", null, null, null, false)).get(0).avgCents())
                .isEqualTo(usd(30_000));
    }

    @Test
    void emptyResultSetsAreEmptyNotErrors() {
        assertThat(insights.stats(null, InsightsFilter.none())).isEmpty();
        assertThat(insights.stats(Dimension.COUNTRY, InsightsFilter.none())).isEmpty();
        assertThat(insights.distribution(InsightsFilter.none(), 20_000)).isEmpty();
        assertThat(insights.earners(InsightsFilter.none(), true, 5)).isEmpty();
    }

    @Test
    void distributionIncludesEmptyBucketsBetweenTheExtremes() {
        saveUsd("US", "Engineering", 10_000, 45_000, 50_000);

        List<DistributionBucket> histogram = insights.distribution(InsightsFilter.none(), 20_000);

        assertThat(histogram).containsExactly(
                new DistributionBucket(0, 20_000, 1),
                new DistributionBucket(20_000, 40_000, 0),
                new DistributionBucket(40_000, 60_000, 2));
    }

    @Test
    void distributionBucketBoundaryIsInclusiveOnTheLowerEdge() {
        saveUsd("US", "Engineering", 20_000, 39_999);

        assertThat(insights.distribution(InsightsFilter.none(), 20_000))
                .containsExactly(new DistributionBucket(20_000, 40_000, 2));
    }

    @Test
    void earnersAreRankedByUsdNotByLocalAmount() {
        // 5,000,000 JPY (~ USD 33k) has a bigger local number than USD 90,000 but earns less.
        save("JP", "Engineering", "L3", "JPY", 5_000_000L);
        save("US", "Engineering", "L3", "USD", usd(90_000));
        save("IN", "Engineering", "L3", "INR", 830_000_000L); // USD 100,000

        var top = insights.earners(InsightsFilter.none(), true, 2);
        var bottom = insights.earners(InsightsFilter.none(), false, 1);

        assertThat(top).extracting(EarnerResponse::currency).containsExactly("INR", "USD");
        assertThat(bottom.get(0).currency()).isEqualTo("JPY");
        assertThat(bottom.get(0).usdCents()).isEqualTo(3_333_333L); // 5,000,000 / 150 = 33,333.33
    }
}
