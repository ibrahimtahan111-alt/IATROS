-- pg_trgm: fast partial-match search (ILIKE '%term%') on patient names, phone, and email.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE patient (
    id                       uuid         PRIMARY KEY,
    clinic_id                uuid         NOT NULL REFERENCES clinic (id),
    first_name               varchar(100) NOT NULL CHECK (btrim(first_name) <> ''),
    last_name                varchar(100) NOT NULL CHECK (btrim(last_name) <> ''),
    date_of_birth            date         NOT NULL CHECK (date_of_birth >= DATE '1900-01-01'),
    gender                   varchar(20)  CHECK (gender IN ('FEMALE', 'MALE', 'OTHER', 'UNDISCLOSED')),
    phone                    varchar(40),
    email                    citext       CHECK (length(email) <= 254),
    address                  varchar(500),
    emergency_contact_name   varchar(200),
    emergency_contact_phone  varchar(40),
    status                   varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    archived_at              timestamptz,
    search_text              text         GENERATED ALWAYS AS (
                                 first_name || ' ' || last_name || ' '
                                 || coalesce(phone, '') || ' ' || coalesce(email::text, '')
                             ) STORED,
    created_by               uuid,
    updated_by               uuid,
    version                  bigint       NOT NULL DEFAULT 0,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT patient_clinic_id_uk UNIQUE (clinic_id, id),
    CONSTRAINT patient_created_by_fk FOREIGN KEY (clinic_id, created_by) REFERENCES app_user (clinic_id, id),
    CONSTRAINT patient_updated_by_fk FOREIGN KEY (clinic_id, updated_by) REFERENCES app_user (clinic_id, id),
    CONSTRAINT patient_archived_at_matches_status CHECK ((status = 'ARCHIVED') = (archived_at IS NOT NULL))
);

CREATE INDEX patient_clinic_name_idx ON patient (clinic_id, last_name, first_name, id);
CREATE INDEX patient_clinic_created_idx ON patient (clinic_id, created_at);
CREATE INDEX patient_search_trgm_idx ON patient USING gin (search_text gin_trgm_ops);

-- Doctor access to patients (roles-and-permissions.md §10). The primary doctor is stored
-- here only (relationship_type = PRIMARY); at most one per patient.

CREATE TABLE patient_doctor (
    id                 uuid        PRIMARY KEY,
    clinic_id          uuid        NOT NULL,
    patient_id         uuid        NOT NULL,
    doctor_id          uuid        NOT NULL,
    relationship_type  varchar(20) NOT NULL CHECK (relationship_type IN ('PRIMARY', 'AUTHORIZED')),
    created_by         uuid,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT patient_doctor_uk UNIQUE (patient_id, doctor_id),
    CONSTRAINT patient_doctor_patient_fk FOREIGN KEY (clinic_id, patient_id) REFERENCES patient (clinic_id, id),
    CONSTRAINT patient_doctor_doctor_fk FOREIGN KEY (clinic_id, doctor_id) REFERENCES doctor (clinic_id, id),
    CONSTRAINT patient_doctor_created_by_fk FOREIGN KEY (clinic_id, created_by) REFERENCES app_user (clinic_id, id)
);

CREATE UNIQUE INDEX patient_doctor_one_primary_uk ON patient_doctor (patient_id) WHERE relationship_type = 'PRIMARY';
CREATE INDEX patient_doctor_doctor_idx ON patient_doctor (doctor_id, patient_id);
