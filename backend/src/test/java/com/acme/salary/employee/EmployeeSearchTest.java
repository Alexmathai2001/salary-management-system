package com.acme.salary.employee;

import static com.acme.salary.employee.TestEmployees.anEmployee;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Exercises the directory filters against real SQLite so the generated SQL is what is tested. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmployeeSearchTest {

    @Autowired
    EmployeeRepository repository;

    @Autowired
    EmployeeService service;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.saveAll(List.of(
                anEmployee().name("Asha", "Rao").email("asha@acme.test").country("IN")
                        .department("Engineering").jobTitle("Backend Engineer").level("L4").salary(2_000_000).build(),
                anEmployee().name("Bruno", "Keller").email("bruno@acme.test").country("DE")
                        .department("Engineering").jobTitle("Backend Engineer").level("L3").salary(6_000_000).build(),
                anEmployee().name("Chen", "Wei").email("chen@acme.test").country("SG")
                        .department("Sales").jobTitle("Account Executive").level("L3").salary(5_000_000).build(),
                anEmployee().name("Dana", "Rao").email("dana@acme.test").country("IN")
                        .department("Sales").jobTitle("Account Executive").level("L2").salary(900_000)
                        .status(EmployeeStatus.INACTIVE).build()));
        repository.flush();
    }

    private List<String> firstNames(EmployeeFilter filter) {
        return service.search(filter, 0, 25, "name", false).getContent().stream()
                .map(Employee::getFirstName).toList();
    }

    private static EmployeeFilter filter(String q, String country, String department, EmployeeStatus status) {
        return new EmployeeFilter(q, country, department, null, null, status);
    }

    @Test
    void noFilterReturnsEveryone() {
        // Default sort is last name: Keller, Rao, Rao (tie broken by id: Asha before Dana), Wei.
        assertThat(firstNames(filter(null, null, null, null))).containsExactly("Bruno", "Asha", "Dana", "Chen");
    }

    @Test
    void filtersByCountryCaseInsensitively() {
        assertThat(firstNames(filter(null, "in", null, null))).containsExactlyInAnyOrder("Asha", "Dana");
    }

    @Test
    void combinesFiltersWithAnd() {
        assertThat(firstNames(filter(null, "IN", "Sales", null))).containsExactly("Dana");
    }

    @Test
    void filtersByStatus() {
        assertThat(firstNames(filter(null, null, null, EmployeeStatus.INACTIVE))).containsExactly("Dana");
    }

    @Test
    void textSearchMatchesNamePartsFullNameAndEmail() {
        assertThat(firstNames(filter("rao", null, null, null))).containsExactlyInAnyOrder("Asha", "Dana");
        assertThat(firstNames(filter("Asha Rao", null, null, null))).containsExactly("Asha");
        assertThat(firstNames(filter("bruno@", null, null, null))).containsExactly("Bruno");
    }

    @Test
    void textSearchTreatsWildcardsLiterally() {
        assertThat(firstNames(filter("%", null, null, null))).isEmpty();
        assertThat(firstNames(filter("_", null, null, null))).isEmpty();
    }

    @Test
    void blankFiltersAreIgnored() {
        assertThat(firstNames(filter("  ", " ", "", null))).hasSize(4);
    }

    @Test
    void sortsBySalaryDescending() {
        var page = service.search(filter(null, null, null, null), 0, 25, "salary", true);

        assertThat(page.getContent()).extracting(Employee::getFirstName)
                .containsExactly("Bruno", "Chen", "Asha", "Dana");
    }

    @Test
    void pagesResultsAndReportsTotals() {
        var first = service.search(filter(null, null, null, null), 0, 3, "name", false);
        var second = service.search(filter(null, null, null, null), 1, 3, "name", false);

        assertThat(first.getTotalElements()).isEqualTo(4);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).hasSize(3);
        assertThat(second.getContent()).hasSize(1);
    }
}
