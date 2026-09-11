package com.example.therapify.enums;

/**
 * Lifecycle of an appointment. Persisted with @Enumerated(EnumType.STRING), so the constant
 * name is both the column value and what the API returns.
 *
 * The distinction that matters is between the two terminal states: COMPLETED asserts the
 * session took place (and is what unlocks leaving a review), while EXPIRED only says the slot
 * went by. Anything the system cannot vouch for has to land in EXPIRED — see
 * AppointmentCompletionJob.
 *
 * Adding a constant here also changes the database: Hibernate generates a check constraint from
 * these values, and ddl-auto=update never rewrites an existing one, so
 * AppointmentStatusConstraintInitializer re-creates it from this enum on every startup.
 */
public enum Status {

    /** Booked by the patient; the professional has not accepted the slot yet. */
    PENDING,

    /** The professional accepted the slot. */
    CONFIRMED,

    /** Terminal: a CONFIRMED appointment whose time has passed, assumed to have happened. */
    COMPLETED,

    /**
     * Terminal: the slot passed while still PENDING, so there never was an agreement. It does
     * not assert that any session happened, and therefore never unlocks a review.
     */
    EXPIRED
}
