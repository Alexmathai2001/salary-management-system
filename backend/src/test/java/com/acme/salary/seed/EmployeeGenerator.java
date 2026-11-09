package com.acme.salary.seed;

import com.acme.salary.employee.EmployeeStatus;
import com.acme.salary.seed.SeedEmployee.SeedSalaryChange;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates realistic, DETERMINISTIC employee data: the same (seed, count, referenceDate) always yields
 * the same output, so tests, demos and performance checks are reproducible.
 *
 * <p>Salary model: base USD pay by level x department factor x country factor x FX rate x noise. About 1% of
 * employees are deliberate outliers (far above/below peers) so the outlier view has something to find.
 */
public final class EmployeeGenerator {

    record Country(String code, String currency, double payFactor, double fxPerUsd) {
    }

    static final List<Country> COUNTRIES = List.of(
            new Country("IN", "INR", 0.30, 83.0),
            new Country("US", "USD", 1.00, 1.0),
            new Country("GB", "GBP", 0.75, 0.79),
            new Country("DE", "EUR", 0.75, 0.92),
            new Country("FR", "EUR", 0.70, 0.92),
            new Country("SG", "SGD", 0.80, 1.35),
            new Country("AU", "AUD", 0.80, 1.50),
            new Country("CA", "CAD", 0.80, 1.36),
            new Country("JP", "JPY", 0.70, 150.0),
            new Country("BR", "BRL", 0.35, 5.0));
    /** Relative headcount per country (same order as COUNTRIES). */
    private static final int[] COUNTRY_WEIGHTS = {25, 20, 10, 10, 5, 8, 6, 6, 5, 5};

    static final List<String> LEVELS = List.of("L1", "L2", "L3", "L4", "L5", "L6");
    private static final int[] LEVEL_WEIGHTS = {15, 25, 30, 20, 8, 2};
    private static final double[] LEVEL_BASE_USD = {45_000, 65_000, 90_000, 120_000, 160_000, 210_000};

    static final Map<String, Double> DEPARTMENT_FACTOR = Map.of(
            "Engineering", 1.15, "Product", 1.10, "Data", 1.10, "Finance", 1.00, "Sales", 0.95,
            "Marketing", 0.90, "Human Resources", 0.85, "Operations", 0.85, "Customer Support", 0.70, "Legal", 1.05);
    static final List<String> DEPARTMENTS = List.of(
            "Engineering", "Product", "Data", "Finance", "Sales", "Marketing", "Human Resources", "Operations",
            "Customer Support", "Legal");
    private static final int[] DEPARTMENT_WEIGHTS = {35, 8, 8, 6, 14, 6, 4, 8, 9, 2};

    static final Map<String, List<String>> JOB_TITLES = Map.of(
            "Engineering", List.of("Software Engineer", "QA Engineer", "DevOps Engineer", "Engineering Manager"),
            "Product", List.of("Product Manager", "Product Designer", "Business Analyst"),
            "Data", List.of("Data Analyst", "Data Engineer", "Data Scientist"),
            "Finance", List.of("Accountant", "Financial Analyst", "Controller"),
            "Sales", List.of("Account Executive", "Sales Development Rep", "Sales Manager"),
            "Marketing", List.of("Marketing Specialist", "Content Strategist", "Brand Manager"),
            "Human Resources", List.of("HR Generalist", "Recruiter", "HR Business Partner"),
            "Operations", List.of("Operations Analyst", "Program Manager", "Facilities Coordinator"),
            "Customer Support", List.of("Support Specialist", "Support Lead", "Customer Success Manager"),
            "Legal", List.of("Legal Counsel", "Compliance Analyst", "Paralegal"));

    private static final List<String> FIRST_NAMES = List.of(
            "Asha", "Bruno", "Chen", "Dana", "Elena", "Farid", "Grace", "Hiro", "Isabel", "Jamal", "Kavya", "Liam",
            "Mei", "Noah", "Olivia", "Priya", "Quentin", "Rohan", "Sofia", "Tariq", "Uma", "Victor", "Wen", "Ximena",
            "Yusuf", "Zara", "Aarav", "Beatriz", "Carlos", "Diya", "Emma", "Felix", "Gita", "Hans", "Ines", "Jun",
            "Kenji", "Lena", "Mateo", "Nia");
    private static final List<String> LAST_NAMES = List.of(
            "Rao", "Keller", "Wei", "Silva", "Garcia", "Khan", "Smith", "Tanaka", "Muller", "Nguyen", "Patel", "Brown",
            "Lopez", "Ivanov", "Singh", "Dubois", "Rossi", "Kim", "Okafor", "Haddad", "Jensen", "Costa", "Sharma",
            "Fischer", "Moreau", "Yamamoto", "Oliveira", "Reddy", "Walker", "Chen", "Iyer", "Schmidt", "Santos",
            "Ahmed", "Taylor", "Mehta", "Novak", "Larsen", "Gupta", "Martin");

    private static final LocalDate EARLIEST_HIRE = LocalDate.of(2015, 1, 1);

    private final long rngSeed;
    private final LocalDate referenceDate;

    public EmployeeGenerator(long rngSeed, LocalDate referenceDate) {
        this.rngSeed = rngSeed;
        this.referenceDate = referenceDate;
    }

    public List<SeedEmployee> generate(int count) {
        Random rnd = new Random(rngSeed);
        List<SeedEmployee> result = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            result.add(one(i, rnd));
        }
        return result;
    }

    private SeedEmployee one(int index, Random rnd) {
        Country country = COUNTRIES.get(weighted(rnd, COUNTRY_WEIGHTS));
        String department = DEPARTMENTS.get(weighted(rnd, DEPARTMENT_WEIGHTS));
        List<String> titles = JOB_TITLES.get(department);
        String jobTitle = titles.get(rnd.nextInt(titles.size()));
        int levelIdx = weighted(rnd, LEVEL_WEIGHTS);
        String first = FIRST_NAMES.get(rnd.nextInt(FIRST_NAMES.size()));
        String last = LAST_NAMES.get(rnd.nextInt(LAST_NAMES.size()));

        long daysSpan = ChronoUnit.DAYS.between(EARLIEST_HIRE, referenceDate);
        LocalDate hireDate = EARLIEST_HIRE.plusDays((long) (rnd.nextDouble() * daysSpan));

        long salaryMinor = currentSalaryMinor(country, department, levelIdx, rnd);
        List<SeedSalaryChange> changes = salaryHistory(salaryMinor, hireDate, rnd);

        EmployeeStatus status = rnd.nextInt(100) < 5 ? EmployeeStatus.INACTIVE : EmployeeStatus.ACTIVE;
        String email = (first + "." + last + "." + index + "@acme.example").toLowerCase();

        return new SeedEmployee(String.format("EMP-%06d", index), first, last, email, country.code(), department,
                jobTitle, LEVELS.get(levelIdx), country.currency(), salaryMinor, hireDate, status, changes);
    }

    private long currentSalaryMinor(Country country, String department, int levelIdx, Random rnd) {
        double noise = 1 + clamp(rnd.nextGaussian() * 0.08, -0.25, 0.25);
        int roll = rnd.nextInt(100);
        if (roll == 0) {
            noise *= 1.7; // deliberate high outlier (~1%)
        } else if (roll == 1) {
            noise *= 0.55; // deliberate low outlier (~1%)
        }
        double usd = LEVEL_BASE_USD[levelIdx] * DEPARTMENT_FACTOR.get(department) * country.payFactor() * noise;
        double major = Math.round(usd * country.fxPerUsd() / 100.0) * 100.0; // round to a "nice" figure
        return toMinor(major, country.currency());
    }

    /** Builds history oldest-first by working backwards from today's salary through 0-3 raises. */
    private List<SeedSalaryChange> salaryHistory(long currentMinor, LocalDate hireDate, Random rnd) {
        long tenureDays = ChronoUnit.DAYS.between(hireDate, referenceDate);
        int raises = 0;
        if (tenureDays > 365 && rnd.nextInt(100) < 35) {
            raises = 1 + rnd.nextInt(3);
        }

        long[] salaries = new long[raises + 1]; // salaries[0] = starting salary ... salaries[raises] = current
        salaries[raises] = currentMinor;
        for (int r = raises - 1; r >= 0; r--) {
            double pct = 0.03 + rnd.nextDouble() * 0.09; // 3-12% raise
            salaries[r] = Math.max(1, Math.round(salaries[r + 1] / (1 + pct)));
            if (salaries[r] == salaries[r + 1]) {
                salaries[r] = Math.max(1, salaries[r + 1] - 1);
            }
        }

        List<SeedSalaryChange> changes = new ArrayList<>(raises + 1);
        changes.add(new SeedSalaryChange(null, salaries[0], hireDate, "Initial salary"));
        long step = tenureDays / (raises + 1);
        for (int r = 1; r <= raises; r++) {
            LocalDate effective = hireDate.plusDays(step * r);
            changes.add(new SeedSalaryChange(salaries[r - 1], salaries[r], effective,
                    r == raises ? "Annual review" : "Promotion / adjustment"));
        }
        return changes;
    }

    /** Converts a major-unit amount to minor units using the currency's real fraction digits (JPY has 0). */
    static long toMinor(double major, String currencyCode) {
        int digits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        return Math.round(major * Math.pow(10, digits));
    }

    private static int weighted(Random rnd, int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int pick = rnd.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            pick -= weights[i];
            if (pick < 0) {
                return i;
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
