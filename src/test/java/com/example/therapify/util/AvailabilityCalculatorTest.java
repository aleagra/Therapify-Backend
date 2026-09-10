package com.example.therapify.util;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityCalculatorTest {

    // Anchor on a known Monday so day-of-week math in the test is deterministic
    // regardless of what "today" happens to be when the suite runs.
    private static final LocalDate MONDAY = LocalDate.now()
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Test
    void countsAllSlotsInWindowWhenNoneAreBooked() {
        Map<String, List<String>> availability = Map.of(
                "MONDAY", List.of("09:00", "10:00"),
                "WEDNESDAY", List.of("14:00")
        );

        AvailabilityCalculator.Result result = AvailabilityCalculator.compute(
                availability, Set.of(), MONDAY, MONDAY.plusDays(6), 5
        );

        // Monday (2 slots) + Wednesday (1 slot) within the 7-day window
        assertEquals(3, result.availableSlotsCount());
        assertEquals(List.of(MONDAY, MONDAY.plusDays(2)), result.nextAvailableDates());
    }

    @Test
    void excludesSlotsAlreadyBooked() {
        Map<String, List<String>> availability = Map.of(
                "MONDAY", List.of("09:00", "10:00")
        );
        Set<String> booked = Set.of(AvailabilityCalculator.slotKey(MONDAY, java.time.LocalTime.parse("09:00")));

        AvailabilityCalculator.Result result = AvailabilityCalculator.compute(
                availability, booked, MONDAY, MONDAY, 5
        );

        assertEquals(1, result.availableSlotsCount());
        assertEquals(List.of(MONDAY), result.nextAvailableDates());
    }

    @Test
    void dateWithAllSlotsBookedIsNotReturnedAsNextAvailable() {
        Map<String, List<String>> availability = Map.of(
                "MONDAY", List.of("09:00")
        );
        Set<String> booked = Set.of(AvailabilityCalculator.slotKey(MONDAY, java.time.LocalTime.parse("09:00")));

        AvailabilityCalculator.Result result = AvailabilityCalculator.compute(
                availability, booked, MONDAY, MONDAY, 5
        );

        assertEquals(0, result.availableSlotsCount());
        assertTrue(result.nextAvailableDates().isEmpty());
    }

    @Test
    void matchesSpanishDayNamesAsFallback() {
        Map<String, List<String>> availability = Map.of(
                "lunes", List.of("09:00")
        );

        AvailabilityCalculator.Result result = AvailabilityCalculator.compute(
                availability, Set.of(), MONDAY, MONDAY, 5
        );

        assertEquals(1, result.availableSlotsCount());
    }

    @Test
    void respectsMaxNextAvailableDatesLimit() {
        Map<String, List<String>> availability = Map.of(
                "MONDAY", List.of("09:00"),
                "TUESDAY", List.of("09:00"),
                "WEDNESDAY", List.of("09:00")
        );

        AvailabilityCalculator.Result result = AvailabilityCalculator.compute(
                availability, Set.of(), MONDAY, MONDAY.plusDays(2), 2
        );

        assertEquals(3, result.availableSlotsCount());
        assertEquals(2, result.nextAvailableDates().size());
    }

    @Test
    void returnsEmptyResultWhenNoAvailabilityConfigured() {
        AvailabilityCalculator.Result result = AvailabilityCalculator.compute(
                null, Set.of(), MONDAY, MONDAY.plusDays(30), 5
        );

        assertEquals(0, result.availableSlotsCount());
        assertTrue(result.nextAvailableDates().isEmpty());
    }

    @Test
    void ignoresUnparseableTimeStrings() {
        Map<String, List<String>> availability = Map.of(
                "MONDAY", List.of("not-a-time", "09:00")
        );

        AvailabilityCalculator.Result result = AvailabilityCalculator.compute(
                availability, Set.of(), MONDAY, MONDAY, 5
        );

        assertEquals(1, result.availableSlotsCount());
    }
}
