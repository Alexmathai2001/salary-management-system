package com.acme.salary.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.employee.EmployeeStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Pure unit tests: the generator is deterministic and its output satisfies the data invariants. */
class EmployeeGeneratorTest {

    static final LocalDate REF = LocalDate.of(2025, 6, 1);
    static final List<SeedEmployee> TEN_K = new EmployeeGenerator(42, REF).generate(10_000);

    @Test
    void sameSeedProducesIdenticalData() {
        var a = new EmployeeGenerator(7, REF).generate(300);
        var b = new EmployeeGenerator(7, REF).generate(300);

        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentSeedProducesDifferentData() {
        var a = new EmployeeGenerator(1, REF).generate(100);
        var b = new EmployeeGenerator(2, REF).generate(100);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void generatesRequestedCountWithUniqueCodesAndEmails() {
        assertThat(TEN_K).hasSize(10_000);
        assertThat(TEN_K.stream().map(SeedEmployee::employeeCode).distinct()).hasSize(10_000);
        assertThat(TEN_K.stream().map(SeedEmployee::email).distinct()).hasSize(10_000);
        assertThat(TEN_K.get(0).employeeCode()).isEqualTo("EMP-000001");
        assertThat(TEN_K.get(9_999).employeeCode()).isEqualTo("EMP-010000");
    }

    @Test
    void coversAllCountriesAndDepartments() {
        assertThat(TEN_K.stream().map(SeedEmployee::country).distinct()).hasSize(10);
        assertThat(TEN_K.stream().map(SeedEmployee::department).distinct()).hasSize(10);
        assertThat(TEN_K.stream().map(SeedEmployee::jobLevel).distinct()).containsExactlyInAnyOrder(
                "L1", "L2", "L3", "L4", "L5", "L6");
    }

    @Test
    void everyEmployeeHasConsistentCountryCurrencyAndPositiveSalary() {
        Map<String, String> currencyByCountry = EmployeeGenerator.COUNTRIES.stream()
                .collect(Collectors.toMap(EmployeeGenerator.Country::code, EmployeeGenerator.Country::currency));

        assertThat(TEN_K).allSatisfy(e -> {
            assertThat(e.currency()).isEqualTo(currencyByCountry.get(e.country()));
            assertThat(e.salaryMinor()).isPositive();
            assertThat(EmployeeGenerator.JOB_TITLES.get(e.department())).contains(e.jobTitle());
            assertThat(e.hireDate()).isBetween(LocalDate.of(2015, 1, 1), REF);
        });
    }

    @Test
    void salaryHistoryIsInternallyConsistent() {
        assertThat(TEN_K).allSatisfy(e -> {
            var changes = e.salaryChanges();
            assertThat(changes).isNotEmpty();
            assertThat(changes.get(0).oldSalaryMinor()).isNull();
            assertThat(changes.get(0).effectiveDate()).isEqualTo(e.hireDate());
            assertThat(changes.get(changes.size() - 1).newSalaryMinor()).isEqualTo(e.salaryMinor());
            for (int i = 1; i < changes.size(); i++) {
                assertThat(changes.get(i).oldSalaryMinor()).isEqualTo(changes.get(i - 1).newSalaryMinor());
                assertThat(changes.get(i).effectiveDate()).isAfterOrEqualTo(changes.get(i - 1).effectiveDate());
                assertThat(changes.get(i).newSalaryMinor()).isGreaterThan(changes.get(i).oldSalaryMinor());
            }
            assertThat(changes.get(changes.size() - 1).effectiveDate()).isBeforeOrEqualTo(REF);
        });
    }

    @Test
    void roughlyAThirdOfEmployeesHaveHadARaise() {
        long withRaise = TEN_K.stream().filter(e -> e.salaryChanges().size() > 1).count();

        assertThat(withRaise).isBetween(2_000L, 4_500L);
    }

    @Test
    void aboutFivePercentAreInactive() {
        long inactive = TEN_K.stream().filter(e -> e.status() == EmployeeStatus.INACTIVE).count();

        assertThat(inactive).isBetween(350L, 650L);
    }

    @Test
    void higherLevelsEarnMoreWithinTheSameCountryAndDepartment() {
        double l1 = averageSalary("US", "Engineering", "L1");
        double l3 = averageSalary("US", "Engineering", "L3");
        double l5 = averageSalary("US", "Engineering", "L5");

        assertThat(l1).isLessThan(l3);
        assertThat(l3).isLessThan(l5);
    }

    @Test
    void currencyWithNoMinorUnitsIsNotScaledByHundred() {
        // JPY has 0 fraction digits: 5,000,000 yen is stored as 5,000,000, not 500,000,000.
        assertThat(EmployeeGenerator.toMinor(5_000_000, "JPY")).isEqualTo(5_000_000L);
        assertThat(EmployeeGenerator.toMinor(85_000, "USD")).isEqualTo(8_500_000L);
    }

    @Test
    void containsDeliberateOutliersForTheOutlierView() {
        // Within US/Engineering/L3, at least one salary should sit far from the group's median.
        List<Long> salaries = TEN_K.stream()
                .filter(e -> e.country().equals("US") && e.department().equals("Engineering")
                        && e.jobLevel().equals("L3"))
                .map(SeedEmployee::salaryMinor).sorted().toList();
        double median = salaries.get(salaries.size() / 2);

        assertThat(salaries.get(salaries.size() - 1) / median).isGreaterThan(1.4);
    }

    private static double averageSalary(String country, String department, String level) {
        return TEN_K.stream()
                .filter(e -> e.country().equals(country) && e.department().equals(department)
                        && e.jobLevel().equals(level))
                .mapToLong(SeedEmployee::salaryMinor).average().orElseThrow();
    }
}
