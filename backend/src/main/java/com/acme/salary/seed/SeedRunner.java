package com.acme.salary.seed;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Seeds demo data at startup when {@code app.seed.enabled=true}. Safe by default: it does nothing if the
 * database already has employees, unless {@code app.seed.reset=true}.
 *
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.seed.enabled=true"
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final SeedDataLoader loader;
    private final int count;
    private final long rngSeed;
    private final boolean reset;

    public SeedRunner(SeedDataLoader loader,
            @Value("${app.seed.count:10000}") int count,
            @Value("${app.seed.rng-seed:42}") long rngSeed,
            @Value("${app.seed.reset:false}") boolean reset) {
        this.loader = loader;
        this.count = count;
        this.rngSeed = rngSeed;
        this.reset = reset;
    }

    @Override
    public void run(ApplicationArguments args) {
        long existing = loader.employeeCount();
        if (existing > 0 && !reset) {
            log.info("Seed skipped: {} employees already present (set app.seed.reset=true to replace)", existing);
            return;
        }
        if (reset) {
            loader.clear();
        }
        long start = System.nanoTime();
        var employees = new EmployeeGenerator(rngSeed, LocalDate.of(2025, 6, 1)).generate(count);
        loader.load(employees);
        log.info("Seeded {} employees in {} ms (rng-seed={})", count, (System.nanoTime() - start) / 1_000_000, rngSeed);
    }
}
