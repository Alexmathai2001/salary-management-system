package com.acme.salary.employee;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/** Test data builder so each test states only the fields it cares about. */
public final class TestEmployees {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final Employee e = new Employee();

    private TestEmployees() {
        int n = SEQ.incrementAndGet();
        e.setEmployeeCode("T-" + n);
        e.setFirstName("First" + n);
        e.setLastName("Last" + n);
        e.setEmail("user" + n + "@acme.test");
        e.setCountry("IN");
        e.setDepartment("Engineering");
        e.setJobTitle("Software Engineer");
        e.setJobLevel("L3");
        e.setCurrency("INR");
        e.setSalaryMinor(1_000_000L);
        e.setHireDate(LocalDate.of(2022, 1, 1));
        e.setStatus(EmployeeStatus.ACTIVE);
        e.setCreatedAt(LocalDateTime.of(2024, 1, 1, 9, 0));
        e.setUpdatedAt(LocalDateTime.of(2024, 1, 1, 9, 0));
    }

    public static TestEmployees anEmployee() {
        return new TestEmployees();
    }

    public TestEmployees name(String first, String last) {
        e.setFirstName(first);
        e.setLastName(last);
        return this;
    }

    public TestEmployees email(String email) {
        e.setEmail(email);
        return this;
    }

    public TestEmployees country(String country) {
        e.setCountry(country);
        return this;
    }

    public TestEmployees department(String department) {
        e.setDepartment(department);
        return this;
    }

    public TestEmployees jobTitle(String jobTitle) {
        e.setJobTitle(jobTitle);
        return this;
    }

    public TestEmployees level(String level) {
        e.setJobLevel(level);
        return this;
    }

    public TestEmployees currency(String currency) {
        e.setCurrency(currency);
        return this;
    }

    public TestEmployees salary(long salaryMinor) {
        e.setSalaryMinor(salaryMinor);
        return this;
    }

    public TestEmployees status(EmployeeStatus status) {
        e.setStatus(status);
        return this;
    }

    public Employee build() {
        return e;
    }
}
