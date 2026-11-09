package com.acme.salary.seed;

import com.acme.salary.employee.EmployeeStatus;
import java.time.LocalDate;
import java.util.List;

/** A generated employee plus their salary history (oldest first). The last change is the current salary. */
public record SeedEmployee(
        String employeeCode,
        String firstName,
        String lastName,
        String email,
        String country,
        String department,
        String jobTitle,
        String jobLevel,
        String currency,
        long salaryMinor,
        LocalDate hireDate,
        EmployeeStatus status,
        List<SeedSalaryChange> salaryChanges) {

    public record SeedSalaryChange(Long oldSalaryMinor, long newSalaryMinor, LocalDate effectiveDate, String reason) {
    }
}
