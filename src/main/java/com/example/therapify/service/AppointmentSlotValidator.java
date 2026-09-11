package com.example.therapify.service;

import com.example.therapify.exception.AppointmentErrorCode;
import com.example.therapify.exception.InvalidAppointmentException;
import com.example.therapify.model.User;
import com.example.therapify.util.AvailabilityCalculator;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * The rules a slot must satisfy to be bookable, shared by POST /appointments and
 * PATCH /appointments/{id}/reschedule.
 *
 * It lives in one place because the two endpoints drifted apart once already: rescheduling
 * validated the weekly template and the clock while booking validated nothing, so a direct POST
 * could create a turn at 03:00, or — the bug that prompted this — a turn for 08:00 booked at
 * 17:57 the same day, born already expired. Anything that decides "is this slot legal" belongs
 * here, so adding a rule can't fix one endpoint and forget the other.
 *
 * Everything throws {@link InvalidAppointmentException} (HTTP 400) with the code the frontend
 * branches on. Slot *availability* is deliberately not here: that one needs the repository and,
 * for reschedule, the appointment's own id to exclude, and it answers 409, not 400.
 */
@Component
public class AppointmentSlotValidator {

    private final Clock clock;

    public AppointmentSlotValidator(Clock clock) {
        this.clock = clock;
    }

    /**
     * Full check for a slot someone wants to occupy, in the order the API documents:
     * coherent time range, in the future, inside the professional's weekly template.
     */
    public void validateBookableSlot(User doctor, LocalDate date, LocalTime startTime, LocalTime endTime) {
        validateTimeRange(startTime, endTime);
        validateIsInTheFuture(date, startTime);
        validateIsInDoctorSchedule(doctor, date, startTime);
    }

    public void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new InvalidAppointmentException(
                    AppointmentErrorCode.INVALID_TIME_RANGE,
                    "La hora de fin debe ser posterior a la de inicio.");
        }
    }

    /**
     * Compared against the server clock, not the browser's: the original bug was a turn booked
     * at 17:57 for 08:00 of the same day, which only the server side can reject reliably.
     */
    public void validateIsInTheFuture(LocalDate date, LocalTime startTime) {
        if (!LocalDateTime.of(date, startTime).isAfter(LocalDateTime.now(clock))) {
            throw new InvalidAppointmentException(
                    AppointmentErrorCode.SLOT_IN_PAST,
                    "El horario del turno debe ser futuro.");
        }
    }

    /**
     * Only enforced when the professional actually has a weekly template: with none configured
     * there is nothing to validate against, and rejecting would block booking entirely.
     */
    public void validateIsInDoctorSchedule(User doctor, LocalDate date, LocalTime startTime) {
        Map<String, List<String>> weeklyAvailability = doctor.getAvailabilityMap();

        if (weeklyAvailability == null || weeklyAvailability.isEmpty()) return;

        if (!AvailabilityCalculator.isSlotInWeeklyTemplate(weeklyAvailability, date, startTime)) {
            throw new InvalidAppointmentException(
                    AppointmentErrorCode.SLOT_NOT_IN_SCHEDULE,
                    "El profesional no atiende en ese horario.");
        }
    }

    public LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidAppointmentException(
                    AppointmentErrorCode.INVALID_DATE_FORMAT, "La fecha es obligatoria.");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidAppointmentException(
                    AppointmentErrorCode.INVALID_DATE_FORMAT,
                    "La fecha debe tener formato YYYY-MM-DD.");
        }
    }

    public LocalTime parseTime(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidAppointmentException(
                    AppointmentErrorCode.INVALID_DATE_FORMAT, field + " es obligatorio.");
        }
        try {
            return LocalTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidAppointmentException(
                    AppointmentErrorCode.INVALID_DATE_FORMAT,
                    field + " debe tener formato HH:mm.");
        }
    }
}
