package com.example.therapify.exception;

/**
 * Machine-readable reason attached to appointment errors, exposed as the "code" field of the
 * error body. The human-readable "mensaje" is for display; this is what the frontend branches
 * on, so several distinct 409s (slot taken vs. reschedule limit vs. 24h window) stay
 * distinguishable without string-matching Spanish text.
 */
public enum AppointmentErrorCode {

    /** 409 — someone else already holds that (doctor, date, startTime). */
    SLOT_TAKEN,

    /** 409 — a COMPLETED appointment is history and cannot be moved. */
    APPOINTMENT_COMPLETED,

    /** 409 — the slot went by while still PENDING; a terminal state, like COMPLETED. */
    APPOINTMENT_EXPIRED,

    /** 409 — less than 24h to the original slot, same window as the cancellation policy. */
    RESCHEDULE_WINDOW_EXPIRED,

    /**
     * 409 — the requested status is not reachable from the current one (see the transition
     * table in AppointmentService.validateTransition): a terminal state is final, and a turn
     * cannot be marked COMPLETED or EXPIRED before its time has actually passed.
     */
    INVALID_STATUS_TRANSITION,

    /** 400 — the status field is missing, blank, or not one of the Status values. */
    INVALID_STATUS,

    /** 409 — already rescheduled the maximum number of times. */
    RESCHEDULE_LIMIT_REACHED,

    /** 400 — the body carries a doctorId other than the appointment's doctor. */
    DOCTOR_CHANGE_NOT_ALLOWED,

    /** 400 — the requested slot is in the past. */
    SLOT_IN_PAST,

    /** 400 — the slot is outside the doctor's weekly availability template. */
    SLOT_NOT_IN_SCHEDULE,

    /** 400 — the new slot is the one the appointment already occupies. */
    SAME_SLOT,

    /** 400 — endTime is not after startTime. */
    INVALID_TIME_RANGE,

    /** 400 — date/startTime/endTime are not parseable as YYYY-MM-DD / HH:mm. */
    INVALID_DATE_FORMAT
}
