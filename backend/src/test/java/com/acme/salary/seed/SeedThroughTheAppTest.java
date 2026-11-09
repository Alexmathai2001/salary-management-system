package com.acme.salary.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.insights.InsightsFilter;
import com.acme.salary.insights.InsightsRepository;
import com.acme.salary.insights.OutlierDetector;
import com.acme.salary.insights.OutlierParams;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/** The application started with the seed context on: the loaded data reads back correctly through JPA and the SQL reports. */
@SpringBootTest(properties = "spring.liquibase.contexts=seed")
@ActiveProfiles("test")
class SeedThroughTheAppTest {

    private static final List<SeedEmployee> SAMPLE = SeedFileWriter.sample();

    @Autowired
    EmployeeRepository employees;

    @Autowired
    InsightsRepository insights;

    @Test
    void jpaSeesAllTheEmployees() {
        assertThat(employees.count()).isEqualTo(SeedFileWriter.EMPLOYEE_COUNT);
    }

    @Test
    void datesAndEnumsReadBackCorrectlyThroughHibernate() {
        Employee first = employees.findAll(PageRequest.of(0, 1)).getContent().get(0);
        SeedEmployee expected = SAMPLE.get(0);

        assertThat(first.getEmployeeCode()).isEqualTo(expected.employeeCode());
        assertThat(first.getHireDate()).isEqualTo(expected.hireDate());
        assertThat(first.getStatus()).isEqualTo(expected.status());
        assertThat(first.getSalaryMinor()).isEqualTo(expected.salaryMinor());
        assertThat(first.getCreatedAt().toLocalDate()).isEqualTo(LocalDate.of(2025, 6, 1));
    }

    @Test
    void inactiveEmployeesAreSeededToo() {
        long inactive = SAMPLE.stream().filter(e -> e.status() == EmployeeStatus.INACTIVE).count();

        assertThat(inactive).isPositive();
        assertThat(employees.count((root, q, cb) -> cb.equal(root.get("status"), EmployeeStatus.INACTIVE))).isEqualTo(inactive);
    }

    @Test
    void insightsRunOverTheSeededData() {
        long active = SAMPLE.stream().filter(e -> e.status() == EmployeeStatus.ACTIVE).count();

        var summary = insights.stats(null, InsightsFilter.none()).get(0);

        assertThat(summary.headcount()).isEqualTo(active);
        assertThat(summary.minCents()).isPositive();
        assertThat(insights.stats(com.acme.salary.insights.Dimension.COUNTRY, InsightsFilter.none())).hasSize(10);
    }

    @Test
    void theOutlierReviewAccountsForEveryActiveEmployeeAndFindsSome() {
        long active = SAMPLE.stream().filter(e -> e.status() == EmployeeStatus.ACTIVE).count();

        var report = OutlierDetector.detect(insights.allPay(InsightsFilter.none()), OutlierParams.defaults());

        assertThat(report.employeesEvaluated() + report.employeesNotEvaluated()).isEqualTo((int) active);
        assertThat(report.totalFlagged()).isPositive(); // the sample contains deliberate outliers
    }
}
