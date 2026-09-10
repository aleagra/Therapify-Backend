package com.example.therapify.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Expands a doctor's recurring weekly availability template (day-of-week -> time slots)
 * over a concrete date range and subtracts already booked slots.
 */
public final class AvailabilityCalculator {

    private static final Map<DayOfWeek, String> SPANISH_DAY_NAMES = Map.of(
            DayOfWeek.MONDAY, "LUNES",
            DayOfWeek.TUESDAY, "MARTES",
            DayOfWeek.WEDNESDAY, "MIERCOLES",
            DayOfWeek.THURSDAY, "JUEVES",
            DayOfWeek.FRIDAY, "VIERNES",
            DayOfWeek.SATURDAY, "SABADO",
            DayOfWeek.SUNDAY, "DOMINGO"
    );

    private AvailabilityCalculator() {}

    public record Result(int availableSlotsCount, List<LocalDate> nextAvailableDates) {
        public static Result empty() {
            return new Result(0, List.of());
        }
    }

    public static Result compute(
            Map<String, List<String>> weeklyAvailability,
            Set<String> bookedSlotKeys,
            LocalDate from,
            LocalDate to,
            int maxDatesToReturn
    ) {
        if (weeklyAvailability == null || weeklyAvailability.isEmpty() || from.isAfter(to)) {
            return Result.empty();
        }

        Map<String, List<String>> normalized = new HashMap<>();
        weeklyAvailability.forEach((day, slots) -> {
            if (day != null) {
                normalized.put(day.trim().toUpperCase(Locale.ROOT), slots);
            }
        });

        int availableCount = 0;
        List<LocalDate> nextAvailableDates = new ArrayList<>();

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<String> daySlots = slotsForDay(normalized, date.getDayOfWeek());
            if (daySlots == null || daySlots.isEmpty()) continue;

            boolean dateHasAvailableSlot = false;

            for (String rawSlot : daySlots) {
                LocalTime time = parseTime(rawSlot);
                if (time == null) continue;

                if (!bookedSlotKeys.contains(slotKey(date, time))) {
                    availableCount++;
                    dateHasAvailableSlot = true;
                }
            }

            if (dateHasAvailableSlot && nextAvailableDates.size() < maxDatesToReturn) {
                nextAvailableDates.add(date);
            }
        }

        return new Result(availableCount, nextAvailableDates);
    }

    public static String slotKey(LocalDate date, LocalTime time) {
        return date + "T" + time;
    }

    private static List<String> slotsForDay(Map<String, List<String>> normalizedAvailability, DayOfWeek dayOfWeek) {
        List<String> byEnglishName = normalizedAvailability.get(dayOfWeek.name());
        if (byEnglishName != null) return byEnglishName;

        String spanishName = SPANISH_DAY_NAMES.get(dayOfWeek);
        return spanishName != null ? normalizedAvailability.get(spanishName) : null;
    }

    private static LocalTime parseTime(String rawSlot) {
        if (rawSlot == null || rawSlot.isBlank()) return null;
        try {
            return LocalTime.parse(rawSlot.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
