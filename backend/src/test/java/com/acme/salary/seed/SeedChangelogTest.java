package com.acme.salary.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs the REAL Liquibase changelog against a fresh in-memory SQLite database (no Spring), so the seed changeset, its
 * precondition and the date conversion are tested exactly as the application runs them.
 */
class SeedChangelogTest {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static final List<SeedEmployee> SAMPLE = SeedFileWriter.sample();
    private static Connection seeded;

    @BeforeAll
    static void seedOnce() throws Exception {
        seeded = DriverManager.getConnection("jdbc:sqlite::memory:");
        migrate(seeded, "seed");
    }

    @AfterAll
    static void close() throws SQLException {
        seeded.close();
    }

    private static void migrate(Connection connection, String contexts) throws Exception {
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        // Deliberately not in try-with-resources: closing Liquibase also closes the JDBC connection, which the test owns.
        new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)
                .update(new Contexts(contexts), new LabelExpression());
    }

    private static long count(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // ---- the data that gets loaded ----

    @Test
    void loadsEveryEmployeeAndEverySalaryChange() throws SQLException {
        long expectedChanges = SAMPLE.stream().mapToLong(e -> e.salaryChanges().size()).sum();

        assertThat(count(seeded, "SELECT COUNT(*) FROM employees")).isEqualTo(SeedFileWriter.EMPLOYEE_COUNT);
        assertThat(count(seeded, "SELECT COUNT(*) FROM salary_history")).isEqualTo(expectedChanges);
    }

    @Test
    void everyLoadedEmployeeMatchesTheGeneratorRowForRow() throws SQLException {
        try (Statement s = seeded.createStatement();
                ResultSet rs = s.executeQuery("SELECT id, employee_code, email, country, currency, salary_minor, hire_date, status "
                        + "FROM employees ORDER BY id")) {
            for (int i = 0; i < SAMPLE.size(); i++) {
                SeedEmployee expected = SAMPLE.get(i);
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("id")).isEqualTo(i + 1);
                assertThat(rs.getString("employee_code")).isEqualTo(expected.employeeCode());
                assertThat(rs.getString("email")).isEqualTo(expected.email());
                assertThat(rs.getString("country")).isEqualTo(expected.country());
                assertThat(rs.getString("currency")).isEqualTo(expected.currency());
                assertThat(rs.getLong("salary_minor")).isEqualTo(expected.salaryMinor());
                assertThat(rs.getLong("hire_date")).isEqualTo(epochMillis(expected.hireDate()));
                assertThat(rs.getString("status")).isEqualTo(expected.status().name());
            }
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    void salaryChangesArePreservedInOrderWithTheirOldAndNewValues() throws SQLException {
        SeedEmployee withRaises = SAMPLE.stream().filter(e -> e.salaryChanges().size() > 2).findFirst().orElseThrow();
        long id = SAMPLE.indexOf(withRaises) + 1;

        try (Statement s = seeded.createStatement();
                ResultSet rs = s.executeQuery("SELECT old_salary_minor, new_salary_minor, reason FROM salary_history "
                        + "WHERE employee_id = " + id + " ORDER BY effective_date, id")) {
            for (var change : withRaises.salaryChanges()) {
                assertThat(rs.next()).isTrue();
                long old = rs.getLong("old_salary_minor");
                if (change.oldSalaryMinor() == null) {
                    assertThat(rs.wasNull()).as("initial salary has no previous value (SQL NULL, not 0)").isTrue();
                } else {
                    assertThat(old).isEqualTo(change.oldSalaryMinor());
                }
                assertThat(rs.getLong("new_salary_minor")).isEqualTo(change.newSalaryMinor());
                assertThat(rs.getString("reason")).isEqualTo(change.reason());
            }
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    void everyEmployeeHasExactlyOneStartingSalary() throws SQLException {
        assertThat(count(seeded, "SELECT COUNT(*) FROM salary_history WHERE old_salary_minor IS NULL"))
                .isEqualTo(SeedFileWriter.EMPLOYEE_COUNT);
        assertThat(count(seeded, "SELECT COUNT(*) FROM salary_history WHERE old_salary_minor = 0")).isZero();
    }

    @Test
    void historyPointsAtRealEmployeesAndEndsAtTheirCurrentSalary() throws SQLException {
        assertThat(count(seeded, "SELECT COUNT(*) FROM salary_history h LEFT JOIN employees e ON e.id = h.employee_id "
                + "WHERE e.id IS NULL")).isZero();
        assertThat(count(seeded, """
                SELECT COUNT(*) FROM employees e
                WHERE e.salary_minor <> (SELECT h.new_salary_minor FROM salary_history h
                                         WHERE h.employee_id = e.id ORDER BY h.effective_date DESC, h.id DESC LIMIT 1)
                """)).isZero();
    }

    @Test
    void everyEmployeeCurrencyHasAnFxRate() throws SQLException {
        assertThat(count(seeded, "SELECT COUNT(*) FROM employees e LEFT JOIN fx_rates f ON f.currency = e.currency "
                + "WHERE f.currency IS NULL")).isZero();
    }

    @Test
    void codesAndEmailsAreUnique() throws SQLException {
        assertThat(count(seeded, "SELECT COUNT(DISTINCT employee_code) FROM employees")).isEqualTo(SeedFileWriter.EMPLOYEE_COUNT);
        assertThat(count(seeded, "SELECT COUNT(DISTINCT email) FROM employees")).isEqualTo(SeedFileWriter.EMPLOYEE_COUNT);
    }

    @Test
    void datesAreConvertedToTheAppsStorageFormat() throws SQLException {
        // The app stores dates as epoch millis at midnight UTC. The CSV has ISO text, so a conversion step must have run.
        assertThat(count(seeded, "SELECT COUNT(*) FROM employees WHERE typeof(hire_date) <> 'integer' OR hire_date % 86400000 <> 0"))
                .isZero();
        assertThat(count(seeded, "SELECT COUNT(*) FROM employees WHERE typeof(created_at) <> 'integer'")).isZero();
        assertThat(count(seeded, "SELECT COUNT(*) FROM salary_history WHERE typeof(effective_date) <> 'integer' "
                + "OR effective_date % 86400000 <> 0")).isZero();
        assertThat(count(seeded, "SELECT COUNT(*) FROM salary_history WHERE typeof(changed_at) <> 'integer'")).isZero();
    }

    // ---- how the changeset is applied ----

    @Test
    void withoutTheSeedContextOnlyTheSchemaIsCreated() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            migrate(c, "test");

            assertThat(count(c, "SELECT COUNT(*) FROM employees")).isZero();
            assertThat(count(c, "SELECT COUNT(*) FROM fx_rates")).isEqualTo(9); // FX rates are schema-level reference data
        }
    }

    @Test
    void runningTheMigrationAgainDoesNotDuplicateTheData() throws Exception {
        migrate(seeded, "seed"); // the shared database is already seeded: a restart must change nothing

        assertThat(count(seeded, "SELECT COUNT(*) FROM employees")).isEqualTo(SeedFileWriter.EMPLOYEE_COUNT);
        assertThat(count(seeded, "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = '003-seed-sample-data'")).isEqualTo(1);
    }

    @Test
    void sampleDataIsNeverMixedIntoADatabaseThatAlreadyHasEmployees() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            migrate(c, "test"); // schema only
            try (Statement s = c.createStatement()) {
                s.executeUpdate("""
                        INSERT INTO employees (id, employee_code, first_name, last_name, email, country, department,
                            job_title, job_level, currency, salary_minor, hire_date, status, created_at, updated_at)
                        VALUES (1, 'REAL-1', 'Real', 'Person', 'real@acme.test', 'IN', 'HR', 'Recruiter', 'L2', 'INR',
                            100, 0, 'ACTIVE', 0, 0)""");
            }

            migrate(c, "seed"); // enabling the seed later must not touch real data

            assertThat(count(c, "SELECT COUNT(*) FROM employees")).isEqualTo(1);
            assertThat(count(c, "SELECT COUNT(*) FROM salary_history")).isZero();
            try (Statement s = c.createStatement();
                    ResultSet rs = s.executeQuery("SELECT EXECTYPE FROM DATABASECHANGELOG WHERE ID = '003-seed-sample-data'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("MARK_RAN");
            }
        }
    }

    private static long epochMillis(LocalDate date) {
        return date.toEpochDay() * 86_400_000L;
    }
}
