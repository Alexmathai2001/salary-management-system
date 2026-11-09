package com.acme.salary.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.SalaryHistoryRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Verifies rows inserted via batched JDBC read back correctly through Hibernate (dates, enums, FKs). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SeedDataLoaderTest {

    @Autowired
    SeedDataLoader loader;

    @Autowired
    EmployeeRepository employees;

    @Autowired
    SalaryHistoryRepository history;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void empty() {
        loader.clear();
    }

    private static java.util.List<SeedEmployee> generate(int n) {
        return new EmployeeGenerator(42, LocalDate.of(2025, 6, 1)).generate(n);
    }

    @Test
    void loadsEmployeesAndHistoryThatHibernateCanRead() {
        var generated = generate(250);

        loader.load(generated);

        assertThat(loader.employeeCount()).isEqualTo(250);
        assertThat(history.count()).isEqualTo(generated.stream().mapToInt(e -> e.salaryChanges().size()).sum());

        SeedEmployee expected = generated.get(10);
        Employee actual = employees.findAll().stream()
                .filter(e -> e.getEmployeeCode().equals(expected.employeeCode())).findFirst().orElseThrow();
        assertThat(actual.getHireDate()).isEqualTo(expected.hireDate());
        assertThat(actual.getSalaryMinor()).isEqualTo(expected.salaryMinor());
        assertThat(actual.getStatus()).isEqualTo(expected.status());
        assertThat(actual.getCreatedAt()).isNotNull();
        assertThat(history.findByEmployeeIdOrderByEffectiveDateDescIdDesc(actual.getId()))
                .hasSize(expected.salaryChanges().size());
    }

    @Test
    void everyHistoryRowPointsAtAnExistingEmployee() {
        loader.load(generate(120));

        Long orphans = jdbc.queryForObject(
                "SELECT COUNT(*) FROM salary_history h LEFT JOIN employees e ON e.id = h.employee_id WHERE e.id IS NULL",
                Long.class);
        assertThat(orphans).isZero();
    }

    @Test
    void currentSalaryEqualsLatestHistoryEntry() {
        loader.load(generate(150));

        Long mismatches = jdbc.queryForObject("""
                SELECT COUNT(*) FROM employees e
                WHERE e.salary_minor <> (SELECT h.new_salary_minor FROM salary_history h
                                         WHERE h.employee_id = e.id ORDER BY h.effective_date DESC, h.id DESC LIMIT 1)
                """, Long.class);
        assertThat(mismatches).isZero();
    }

    @Test
    void runnerDoesNotOverwriteExistingDataUnlessReset() {
        loader.load(generate(30));

        new SeedRunner(loader, 50, 42, false).run(new DefaultApplicationArguments());
        assertThat(loader.employeeCount()).isEqualTo(30);

        new SeedRunner(loader, 50, 42, true).run(new DefaultApplicationArguments());
        assertThat(loader.employeeCount()).isEqualTo(50);
    }
}
