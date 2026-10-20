-- A consultation is the clinical encounter; an appointment is the scheduled slot.
-- At most one consultation per appointment.

CREATE TABLE consultation (
    id                 uuid         PRIMARY KEY,
    clinic_id          uuid         NOT NULL,
    medical_record_id  uuid         NOT NULL,
    doctor_id          uuid         NOT NULL,
    appointment_id     uuid,
    consulted_at       timestamptz  NOT NULL,
    reason             varchar(500),
    diagnosis          text         CHECK (length(diagnosis) <= 4000),
    clinical_notes     text         CHECK (length(clinical_notes) <= 20000),
    treatment_notes    text         CHECK (length(treatment_notes) <= 20000),
    follow_up_notes    text         CHECK (length(follow_up_notes) <= 4000),
    follow_up_on       date,
    created_by         uuid         NOT NULL,
    updated_by         uuid,
    version            bigint       NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT consultation_clinic_id_uk UNIQUE (clinic_id, id),
    CONSTRAINT consultation_appointment_uk UNIQUE (appointment_id),
    CONSTRAINT consultation_record_fk FOREIGN KEY (clinic_id, medical_record_id) REFERENCES medical_record (clinic_id, id),
    CONSTRAINT consultation_doctor_fk FOREIGN KEY (clinic_id, doctor_id) REFERENCES doctor (clinic_id, id),
    CONSTRAINT consultation_appointment_fk FOREIGN KEY (clinic_id, appointment_id) REFERENCES appointment (clinic_id, id),
    CONSTRAINT consultation_created_by_fk FOREIGN KEY (clinic_id, created_by) REFERENCES app_user (clinic_id, id),
    CONSTRAINT consultation_updated_by_fk FOREIGN KEY (clinic_id, updated_by) REFERENCES app_user (clinic_id, id)
);

CREATE INDEX consultation_record_time_idx ON consultation (medical_record_id, consulted_at DESC);
CREATE INDEX consultation_doctor_time_idx ON consultation (doctor_id, consulted_at DESC);
CREATE INDEX consultation_doctor_follow_up_idx ON consultation (doctor_id, follow_up_on) WHERE follow_up_on IS NOT NULL;

-- Document metadata only; file bytes live in DocumentStorage (decisions.md D-032).

CREATE TABLE document (
    id                 uuid         PRIMARY KEY,
    clinic_id          uuid         NOT NULL,
    medical_record_id  uuid         NOT NULL,
    consultation_id    uuid,
    file_name          varchar(255) NOT NULL CHECK (btrim(file_name) <> ''),
    content_type       varchar(100) NOT NULL,
    size_bytes         bigint       NOT NULL CHECK (size_bytes > 0),
    checksum_sha256    char(64)     NOT NULL,
    storage_key        varchar(255) NOT NULL,
    uploaded_by        uuid         NOT NULL,
    archived_at        timestamptz,
    archived_by        uuid,
    created_at         timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT document_storage_key_uk UNIQUE (storage_key),
    CONSTRAINT document_record_fk FOREIGN KEY (clinic_id, medical_record_id) REFERENCES medical_record (clinic_id, id),
    CONSTRAINT document_consultation_fk FOREIGN KEY (clinic_id, consultation_id) REFERENCES consultation (clinic_id, id),
    CONSTRAINT document_uploaded_by_fk FOREIGN KEY (clinic_id, uploaded_by) REFERENCES app_user (clinic_id, id),
    CONSTRAINT document_archived_by_fk FOREIGN KEY (clinic_id, archived_by) REFERENCES app_user (clinic_id, id),
    CONSTRAINT document_archive_pair CHECK ((archived_at IS NULL) = (archived_by IS NULL))
);

CREATE INDEX document_record_created_idx ON document (medical_record_id, created_at DESC) WHERE archived_at IS NULL;
CREATE INDEX document_consultation_idx ON document (consultation_id) WHERE consultation_id IS NOT NULL;
