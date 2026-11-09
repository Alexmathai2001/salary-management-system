package com.acme.salary.employee;

import java.time.LocalDate;

public record EmployeeResponse(
        Long id,
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
        EmployeeStatus status) {

    public static EmployeeResponse from(Employee e) {
        return new EmployeeResponse(
                e.getId(), e.getEmployeeCode(), e.getFirstName(), e.getLastName(), e.getEmail(),
                e.getCountry(), e.getDepartment(), e.getJobTitle(), e.getJobLevel(), e.getCurrency(),
                e.getSalaryMinor(), e.getHireDate(), e.getStatus());
    }
}
