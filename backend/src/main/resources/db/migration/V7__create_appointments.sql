CREATE TABLE appointment (
    id                   uuid         PRIMARY KEY,
    clinic_id            uuid         NOT NULL,
    patient_id           uuid         NOT NULL,
    doctor_id            uuid         NOT NULL,
    start_at             timestamptz  NOT NULL,
    end_at               timestamptz  NOT NULL,
    appointment_type     varchar(20)  NOT NULL CHECK (appointment_type IN
                             ('NEW_PATIENT', 'FOLLOW_UP', 'ROUTINE', 'PROCEDURE', 'URGENT', 'OTHER')),
    status               varchar(20)  NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN
                             ('SCHEDULED', 'CONFIRMED', 'WAITING', 'IN_CONSULTATION', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    reason               varchar(500),
    notes                text         CHECK (length(notes) <= 4000),
    cancellation_reason  varchar(500),
    status_changed_at    timestamptz,
    status_changed_by    uuid,
    created_by           uuid         NOT NULL,
    updated_by           uuid,
    version              bigint       NOT NULL DEFAULT 0,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT appointment_clinic_id_uk UNIQUE (clinic_id, id),
    CONSTRAINT appointment_patient_fk FOREIGN KEY (clinic_id, patient_id) REFERENCES patient (clinic_id, id),
    CONSTRAINT appointment_doctor_fk FOREIGN KEY (clinic_id, doctor_id) REFERENCES doctor (clinic_id, id),
    CONSTRAINT appointment_created_by_fk FOREIGN KEY (clinic_id, created_by) REFERENCES app_user (clinic_id, id),
    CONSTRAINT appointment_updated_by_fk FOREIGN KEY (clinic_id, updated_by) REFERENCES app_user (clinic_id, id),
    CONSTRAINT appointment_status_changed_by_fk FOREIGN KEY (clinic_id, status_changed_by) REFERENCES app_user (clinic_id, id),

    CONSTRAINT appointment_time_order CHECK (end_at > start_at),
    CONSTRAINT appointment_max_duration CHECK (end_at - start_at <= INTERVAL '12 hours'),
    CONSTRAINT appointment_cancellation_reason_only_when_cancelled CHECK (status = 'CANCELLED' OR cancellation_reason IS NULL),

    -- Final guarantee against double-booking, including concurrent requests (decisions.md D-005).
    CONSTRAINT appointment_no_overlap EXCLUDE USING gist (
        doctor_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status NOT IN ('CANCELLED', 'NO_SHOW'))
);

CREATE INDEX appointment_clinic_start_idx ON appointment (clinic_id, start_at);
CREATE INDEX appointment_doctor_start_idx ON appointment (doctor_id, start_at);
CREATE INDEX appointment_patient_start_idx ON appointment (patient_id, start_at DESC);
