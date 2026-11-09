package com.acme.salary.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The committed seed files are generated output. If someone edits one by hand, or changes the generator, this fails. */
class SeedFileTest {

    private static String committed(String name) throws IOException {
        try (InputStream in = SeedFileTest.class.getResourceAsStream("/db/seed/" + name)) {
            assertThat(in).as("db/seed/" + name + " is on the classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final List<SeedEmployee> SAMPLE = SeedFileWriter.sample();

    @Test
    void committedEmployeesFileMatchesTheGenerator() throws IOException {
        assertThat(committed("employees.csv"))
                .as("employees.csv is stale: re-run SeedFileWriter (see its Javadoc) and commit the result")
                .isEqualTo(SeedFileWriter.employeesCsv(SAMPLE));
    }

    @Test
    void committedHistoryFileMatchesTheGenerator() throws IOException {
        assertThat(committed("salary_history.csv"))
                .as("salary_history.csv is stale: re-run SeedFileWriter (see its Javadoc) and commit the result")
                .isEqualTo(SeedFileWriter.historyCsv(SAMPLE));
    }

    @Test
    void theFilesAreReadableCsvWithHeadersAndNoSurprises() throws IOException {
        String employees = committed("employees.csv");
        String[] lines = employees.split("\n");

        assertThat(lines[0]).isEqualTo(SeedFileWriter.EMPLOYEES_HEADER);
        assertThat(lines).hasSize(SeedFileWriter.EMPLOYEE_COUNT + 1);
        assertThat(lines[1]).startsWith("1,EMP-000001,");
        assertThat(employees).doesNotContain("\r"); // stable line endings in git
        assertThat(committed("salary_history.csv").split("\n")[0]).isEqualTo(SeedFileWriter.HISTORY_HEADER);
    }

    @Test
    void historyOfAnEmployeeStartsWithAnEmptyOldSalary() throws IOException {
        String[] lines = committed("salary_history.csv").split("\n");

        assertThat(lines[1]).startsWith("1,NULL,"); // employee 1's initial salary has no previous value
    }

    @Test
    void csvCellsWithCommasQuotesOrNewlinesAreQuoted() {
        String csv = SeedFileWriter.employeesCsv(List.of(new SeedEmployee("EMP-X", "Rao, Asha", "O\"Neil", "a@b.c",
                "IN", "Eng", "Dev", "L1", "INR", 100, java.time.LocalDate.of(2020, 1, 2),
                com.acme.salary.employee.EmployeeStatus.ACTIVE, List.of())));

        assertThat(csv).contains("\"Rao, Asha\"").contains("\"O\"\"Neil\"");
    }
}
