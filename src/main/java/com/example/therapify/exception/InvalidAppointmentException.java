package com.example.therapify.exception;

/**
 * The request is well-formed HTTP but asks for a slot that cannot exist: a past hour, an hour
 * the professional doesn't work, an inverted time range — or, when rescheduling, another
 * professional or the slot the turn already occupies. Always a 400.
 *
 * Kept apart from the generic IllegalArgumentException handler so the whole appointments domain
 * answers with one {error, mensaje, code} envelope. Booking and rescheduling share it because
 * they share the slot rules (see AppointmentSlotValidator); the frontend branches on `code`,
 * which is identical from both endpoints for the same condition.
 */
public class InvalidAppointmentException extends RuntimeException {

    private final AppointmentErrorCode code;

    public InvalidAppointmentException(AppointmentErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AppointmentErrorCode getCode() {
        return code;
    }
}
