-- Generic medical-record foundation (database.md §10–15). Specialty modules extend this
-- with their own tables; they never add columns here.
-- Clinical entries are soft-deleted (decisions.md D-030): deleted_at and deleted_by are set together.

CREATE TABLE medical_record (
    id          uuid        PRIMARY KEY,
    clinic_id   uuid        NOT NULL,
    patient_id  uuid        NOT NULL,
    version     bigint      NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT medical_record_patient_uk UNIQUE (patient_id),
    CONSTRAINT medical_record_clinic_id_uk UNIQUE (clinic_id, id),
    CONSTRAINT medical_record_patient_fk FOREIGN KEY (clinic_id, patient_id) REFERENCES patient (clinic_id, id)
);

CREATE TABLE allergy (
    id                 uuid         PRIMARY KEY,
    medical_record_id  uuid         NOT NULL REFERENCES medical_record (id),
    allergen           varchar(200) NOT NULL CHECK (btrim(allergen) <> ''),
    reaction           varchar(500),
    severity           varchar(20)  CHECK (severity IN ('MILD', 'MODERATE', 'SEVERE', 'UNKNOWN')),
    notes              text         CHECK (length(notes) <= 4000),
    created_by         uuid         NOT NULL REFERENCES app_user (id),
    updated_by         uuid         REFERENCES app_user (id),
    deleted_at         timestamptz,
    deleted_by         uuid         REFERENCES app_user (id),
    version            bigint       NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT allergy_deletion_pair CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

CREATE INDEX allergy_record_idx ON allergy (medical_record_id) WHERE deleted_at IS NULL;

CREATE TABLE medical_condition (
    id                 uuid         PRIMARY KEY,
    medical_record_id  uuid         NOT NULL REFERENCES medical_record (id),
    name               varchar(200) NOT NULL CHECK (btrim(name) <> ''),
    description        text         CHECK (length(description) <= 4000),
    status             varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RESOLVED')),
    diagnosed_on       date,
    created_by         uuid         NOT NULL REFERENCES app_user (id),
    updated_by         uuid         REFERENCES app_user (id),
    deleted_at         timestamptz,
    deleted_by         uuid         REFERENCES app_user (id),
    version            bigint       NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT medical_condition_deletion_pair CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

CREATE INDEX medical_condition_record_idx ON medical_condition (medical_record_id) WHERE deleted_at IS NULL;

CREATE TABLE medication (
    id                 uuid         PRIMARY KEY,
    medical_record_id  uuid         NOT NULL REFERENCES medical_record (id),
    name               varchar(200) NOT NULL CHECK (btrim(name) <> ''),
    dosage             varchar(100),
    frequency          varchar(100),
    notes              text         CHECK (length(notes) <= 4000),
    start_date         date,
    end_date           date,
    active             boolean      NOT NULL DEFAULT true,
    created_by         uuid         NOT NULL REFERENCES app_user (id),
    updated_by         uuid         REFERENCES app_user (id),
    deleted_at         timestamptz,
    deleted_by         uuid         REFERENCES app_user (id),
    version            bigint       NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT medication_dates_ordered CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),
    CONSTRAINT medication_deletion_pair CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

CREATE INDEX medication_record_idx ON medication (medical_record_id) WHERE deleted_at IS NULL;

CREATE TABLE medical_history_entry (
    id                 uuid        PRIMARY KEY,
    medical_record_id  uuid        NOT NULL REFERENCES medical_record (id),
    description        text        NOT NULL CHECK (btrim(description) <> '' AND length(description) <= 4000),
    occurred_on        date,
    notes              text        CHECK (length(notes) <= 4000),
    created_by         uuid        NOT NULL REFERENCES app_user (id),
    updated_by         uuid        REFERENCES app_user (id),
    deleted_at         timestamptz,
    deleted_by         uuid        REFERENCES app_user (id),
    version            bigint      NOT NULL DEFAULT 0,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT medical_history_entry_deletion_pair CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

CREATE INDEX medical_history_entry_record_idx ON medical_history_entry (medical_record_id) WHERE deleted_at IS NULL;

CREATE TABLE medical_note (
    id                 uuid        PRIMARY KEY,
    medical_record_id  uuid        NOT NULL REFERENCES medical_record (id),
    content            text        NOT NULL CHECK (btrim(content) <> '' AND length(content) <= 20000),
    created_by         uuid        NOT NULL REFERENCES app_user (id),
    updated_by         uuid        REFERENCES app_user (id),
    deleted_at         timestamptz,
    deleted_by         uuid        REFERENCES app_user (id),
    version            bigint      NOT NULL DEFAULT 0,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT medical_note_deletion_pair CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

CREATE INDEX medical_note_record_idx ON medical_note (medical_record_id, created_at DESC) WHERE deleted_at IS NULL;
