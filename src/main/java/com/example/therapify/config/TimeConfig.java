package com.example.therapify.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * "Now" as an injectable dependency, so time-dependent rules (the future-slot check, the 24h
 * reschedule window, the hourly completion job) can be unit-tested at a fixed instant instead
 * of waiting for the calendar.
 *
 * About the zone — this matters more than it looks. Appointments are stored as a naive DATE plus
 * a naive TIME: "2026-09-11 17:00" carries no offset, and everyone involved reads it as the
 * professional's wall clock. Meanwhile the JVM is pinned to UTC (see TherapifyApplication) to
 * work around pgjdbc sending a legacy tz alias that postgres:16 rejects. If the business clock
 * follows the JVM, the two disagree by the local offset: at 15:23 in Buenos Aires the server
 * thinks it is 18:23, so booking 17:00 today is refused as SLOT_IN_PAST although it is still
 * an hour and a half away, and the completion job marks a 16:00-17:00 session COMPLETED three
 * hours before it happens.
 *
 * So the zone is configuration, not the JVM default: set therapify.timezone (env
 * THERAPIFY_TIMEZONE) to the zone those stored times are meant to be read in — for Argentina,
 * "America/Argentina/Buenos_Aires" (the canonical id; the "America/Buenos_Aires" alias is the
 * one postgres rejects). The default stays UTC so this changes nothing until it is set
 * deliberately, and only this Clock moves: the JVM default, and therefore the pgjdbc
 * workaround, is untouched.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${therapify.timezone:UTC}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
