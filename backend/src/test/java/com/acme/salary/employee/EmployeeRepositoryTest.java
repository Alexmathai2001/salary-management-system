package com.acme.salary.employee;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Verifies the entity mapping and the Liquibase schema agree, including date and enum round-trips. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmployeeRepositoryTest {

    @Autowired
    EmployeeRepository employees;

    @Autowired
    SalaryHistoryRepository history;

    @Test
    void persistsAndReloadsAnEmployee() {
        Employee saved = employees.saveAndFlush(employee("EMP-T1", "ada@acme.test"));

        Employee loaded = employees.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getEmployeeCode()).isEqualTo("EMP-T1");
        assertThat(loaded.getSalaryMinor()).isEqualTo(1_200_000_00L);
        assertThat(loaded.getHireDate()).isEqualTo(LocalDate.of(2022, 3, 14));
        assertThat(loaded.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    void emailLookupIsCaseInsensitive() {
        employees.saveAndFlush(employee("EMP-T2", "grace@acme.test"));

        assertThat(employees.existsByEmailIgnoreCase("GRACE@ACME.TEST")).isTrue();
        assertThat(employees.existsByEmailIgnoreCase("nobody@acme.test")).isFalse();
    }

    @Test
    void salaryHistoryIsReturnedNewestFirst() {
        Employee e = employees.saveAndFlush(employee("EMP-T3", "linus@acme.test"));
        history.save(entry(e.getId(), null, 100, LocalDate.of(2022, 3, 14)));
        history.save(entry(e.getId(), 100L, 120, LocalDate.of(2023, 4, 1)));
        history.flush();

        var rows = history.findByEmployeeIdOrderByEffectiveDateDescIdDesc(e.getId());

        assertThat(rows).extracting(SalaryHistory::getNewSalaryMinor).containsExactly(120L, 100L);
        assertThat(rows.get(1).getOldSalaryMinor()).isNull();
    }

    private static Employee employee(String code, String email) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setFirstName("Test");
        e.setLastName("User");
        e.setEmail(email);
        e.setCountry("IN");
        e.setDepartment("Engineering");
        e.setJobTitle("Software Engineer");
        e.setJobLevel("L3");
        e.setCurrency("INR");
        e.setSalaryMinor(1_200_000_00L);
        e.setHireDate(LocalDate.of(2022, 3, 14));
        e.setCreatedAt(LocalDateTime.of(2024, 1, 1, 10, 0));
        e.setUpdatedAt(LocalDateTime.of(2024, 1, 1, 10, 0));
        return e;
    }

    private static SalaryHistory entry(Long employeeId, Long oldMinor, long newMinor, LocalDate effective) {
        SalaryHistory h = new SalaryHistory();
        h.setEmployeeId(employeeId);
        h.setOldSalaryMinor(oldMinor);
        h.setNewSalaryMinor(newMinor);
        h.setCurrency("INR");
        h.setEffectiveDate(effective);
        h.setChangedAt(LocalDateTime.of(2024, 1, 1, 10, 0));
        return h;
    }
}
