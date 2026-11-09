package com.acme.salary.seed;

import com.acme.salary.seed.SeedEmployee.SeedSalaryChange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer tool (not part of the running app): renders the deterministic sample data as the two CSV files that the
 * Liquibase seed changeset loads. The files are committed; {@code SeedFileTest} fails if they drift from this generator.
 *
 * <pre>
 * ./mvnw -q test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.acme.salary.seed.SeedFileWriter
 * </pre>
 */
public final class SeedFileWriter {

    public static final long RNG_SEED = 42;
    public static final int EMPLOYEE_COUNT = 10_000;
    public static final LocalDate REFERENCE_DATE = LocalDate.of(2025, 6, 1);
    /** The fixed "recorded at" stamp, so the files never change from run to run. */
    static final String RECORDED_AT = REFERENCE_DATE + " 00:00:00";

    static final String EMPLOYEES_HEADER = "id,employee_code,first_name,last_name,email,country,department,job_title,"
            + "job_level,currency,salary_minor,hire_date,status,created_at,updated_at";
    static final String HISTORY_HEADER =
            "employee_id,old_salary_minor,new_salary_minor,currency,effective_date,reason,changed_at";

    private SeedFileWriter() {
    }

    public static List<SeedEmployee> sample() {
        return new EmployeeGenerator(RNG_SEED, REFERENCE_DATE).generate(EMPLOYEE_COUNT);
    }

    /** One row per employee; ids are 1..n in generation order, which the history file refers to. */
    public static String employeesCsv(List<SeedEmployee> employees) {
        StringBuilder out = new StringBuilder(EMPLOYEES_HEADER).append('\n');
        long id = 1;
        for (SeedEmployee e : employees) {
            out.append(row(String.valueOf(id++), e.employeeCode(), e.firstName(), e.lastName(), e.email(), e.country(),
                    e.department(), e.jobTitle(), e.jobLevel(), e.currency(), String.valueOf(e.salaryMinor()),
                    e.hireDate().toString(), e.status().name(), RECORDED_AT, RECORDED_AT));
        }
        return out.toString();
    }

    /** One row per salary change, oldest first within each employee. A missing old salary is written as NULL. */
    public static String historyCsv(List<SeedEmployee> employees) {
        StringBuilder out = new StringBuilder(HISTORY_HEADER).append('\n');
        long id = 1;
        for (SeedEmployee e : employees) {
            for (SeedSalaryChange c : e.salaryChanges()) {
                out.append(row(String.valueOf(id), c.oldSalaryMinor() == null ? "NULL" : String.valueOf(c.oldSalaryMinor()),
                        String.valueOf(c.newSalaryMinor()), e.currency(), c.effectiveDate().toString(), c.reason(),
                        RECORDED_AT));
            }
            id++;
        }
        return out.toString();
    }

    private static String row(String... cells) {
        List<String> quoted = new ArrayList<>(cells.length);
        for (String cell : cells) {
            quoted.add(cell.contains(",") || cell.contains("\"") || cell.contains("\n")
                    ? "\"" + cell.replace("\"", "\"\"") + "\""
                    : cell);
        }
        return String.join(",", quoted) + "\n";
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "src/main/resources/db/seed");
        Files.createDirectories(dir);
        List<SeedEmployee> employees = sample();
        Files.writeString(dir.resolve("employees.csv"), employeesCsv(employees), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("salary_history.csv"), historyCsv(employees), StandardCharsets.UTF_8);
        System.out.println("Wrote " + employees.size() + " employees to " + dir.toAbsolutePath());
    }
}
