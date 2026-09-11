package com.example.therapify.config;

import com.example.therapify.enums.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Keeps the appointments.status check constraint in sync with {@link Status}.
 *
 * Why this exists: Hibernate derives a check constraint from the enum when it creates the
 * table, but with ddl-auto=update (this project has no Flyway) it never rewrites an existing
 * one. So on any database created before EXPIRED was added, the constraint still reads
 * CHECK (status IN ('PENDING','CONFIRMED','COMPLETED')) and the first write of the new value
 * fails at runtime — exactly the trap that reschedule_count hit from the other direction.
 *
 * The statements are derived from Status.values(), so the constraint can never drift from the
 * enum again: adding a state is a one-line change here by construction. Both statements are
 * idempotent (DROP ... IF EXISTS, then ADD), which makes this safe on every boot, on a fresh
 * `docker compose up` and on Render alike, with no manual step and nothing to remember.
 *
 * Runs as a CommandLineRunner, which Spring Boot invokes after the schema export but *before*
 * publishing ApplicationReadyEvent — and therefore before AppointmentCompletionJob's startup
 * pass, which is the first thing that would write EXPIRED. @Order(0) keeps it ahead of the
 * other runners (DemoDataInitializer).
 */
@Component
@Order(0)
public class AppointmentStatusConstraintInitializer implements CommandLineRunner {

    private static final Logger log =
            LoggerFactory.getLogger(AppointmentStatusConstraintInitializer.class);

    private static final String TABLE = "appointments";
    private static final String CONSTRAINT = "appointments_status_check";

    private final JdbcTemplate jdbcTemplate;

    public AppointmentStatusConstraintInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        String allowedValues = Arrays.stream(Status.values())
                .map(status -> "'" + status.name() + "'")
                .collect(Collectors.joining(", "));

        try {
            // Plain SQL on purpose: "IN (...)" and "DROP CONSTRAINT IF EXISTS" are understood by
            // Postgres and by the H2 the tests run on, so no dialect branching is needed.
            jdbcTemplate.execute(
                    "ALTER TABLE " + TABLE + " DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
            jdbcTemplate.execute(
                    "ALTER TABLE " + TABLE + " ADD CONSTRAINT " + CONSTRAINT
                            + " CHECK (status IN (" + allowedValues + "))");

            log.info("Constraint {} sincronizado con el enum Status: {}", CONSTRAINT, allowedValues);
        } catch (Exception e) {
            // Not fatal: refusing to boot over this would take the whole API down, while the
            // only thing at risk is writing a status the stale constraint rejects (the job logs
            // that failure on its own). Logged as an error because it needs to be acted on.
            log.error("No se pudo sincronizar el constraint {}; los estados nuevos de Status "
                    + "van a ser rechazados por la base hasta que se corrija.", CONSTRAINT, e);
        }
    }
}
