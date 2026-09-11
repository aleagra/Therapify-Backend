package com.example.therapify.exception;

/**
 * The request is well-formed but collides with the current state of the calendar — always a 409.
 *
 * The single-argument constructor keeps the original booking behaviour (POST /appointments)
 * untouched and defaults to {@link AppointmentErrorCode#SLOT_TAKEN}, which is the only reason
 * that path ever conflicts.
 */
public class AppointmentConflictException extends RuntimeException {

    private final AppointmentErrorCode code;

    public AppointmentConflictException(String message) {
        this(AppointmentErrorCode.SLOT_TAKEN, message);
    }

    public AppointmentConflictException(AppointmentErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AppointmentErrorCode getCode() {
        return code;
    }
}
