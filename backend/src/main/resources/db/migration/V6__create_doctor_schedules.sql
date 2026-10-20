-- Recurring weekly working periods. Times are local times in the clinic's time zone
-- (decisions.md D-004). day_of_week follows ISO-8601: 1 = Monday … 7 = Sunday.

CREATE TABLE doctor_schedule_period (
    id           uuid        PRIMARY KEY,
    doctor_id    uuid        NOT NULL REFERENCES doctor (id),
    day_of_week  smallint    NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time   time        NOT NULL,
    end_time     time        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT doctor_schedule_period_time_order CHECK (end_time > start_time),
    -- Periods of the same doctor on the same weekday must not overlap. Times are projected onto
    -- a fixed date only to build a range; the date itself has no meaning.
    CONSTRAINT doctor_schedule_period_no_overlap EXCLUDE USING gist (
        doctor_id WITH =,
        day_of_week WITH =,
        tsrange(DATE '2000-01-03' + start_time, DATE '2000-01-03' + end_time, '[)') WITH &&
    )
);

-- One-off changes to the weekly schedule (decisions.md D-026).
-- A row without start/end time covers the whole day.

CREATE TABLE schedule_exception (
    id              uuid         PRIMARY KEY,
    doctor_id       uuid         NOT NULL REFERENCES doctor (id),
    exception_date  date         NOT NULL,
    start_time      time,
    end_time        time,
    type            varchar(30)  NOT NULL CHECK (type IN ('UNAVAILABLE', 'EXTRA_AVAILABILITY')),
    reason          varchar(500),
    created_by      uuid         REFERENCES app_user (id),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT schedule_exception_time_range CHECK (
        (start_time IS NULL AND end_time IS NULL)
        OR (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)
    ),
    CONSTRAINT schedule_exception_extra_needs_times CHECK (type = 'UNAVAILABLE' OR start_time IS NOT NULL)
);

CREATE INDEX schedule_exception_doctor_date_idx ON schedule_exception (doctor_id, exception_date);
