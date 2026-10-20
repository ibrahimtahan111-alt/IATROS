-- Specialties are controlled reference data identified by stable codes.
-- Display names are translated in the frontend from the code (internationalization.md §10).

CREATE TABLE specialty (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    code        varchar(50) NOT NULL CHECK (code ~ '^[A-Z][A-Z_]*$'),
    active      boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT specialty_code_uk UNIQUE (code)
);

INSERT INTO specialty (code) VALUES
    ('GENERAL_PRACTICE'),
    ('INTERNAL_MEDICINE'),
    ('PEDIATRICS'),
    ('CARDIOLOGY'),
    ('DERMATOLOGY'),
    ('ENDOCRINOLOGY'),
    ('GASTROENTEROLOGY'),
    ('GYNECOLOGY_OBSTETRICS'),
    ('NEPHROLOGY'),
    ('NEUROLOGY'),
    ('ONCOLOGY'),
    ('OPHTHALMOLOGY'),
    ('ORTHOPEDICS'),
    ('OTOLARYNGOLOGY'),
    ('PSYCHIATRY'),
    ('PULMONOLOGY'),
    ('RHEUMATOLOGY'),
    ('UROLOGY'),
    ('DENTISTRY'),
    ('OTHER');

-- A doctor profile extends a user account. The user may be a DOCTOR or the clinic OWNER
-- (a solo doctor owns the clinic and practices in it; decisions.md D-033).

CREATE TABLE doctor (
    id                    uuid         PRIMARY KEY,
    clinic_id             uuid         NOT NULL,
    user_id               uuid         NOT NULL,
    primary_specialty_id  uuid         NOT NULL REFERENCES specialty (id),
    additional_specialty  varchar(200),
    registration_number   varchar(50),
    version               bigint       NOT NULL DEFAULT 0,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT doctor_user_uk UNIQUE (user_id),
    CONSTRAINT doctor_clinic_id_uk UNIQUE (clinic_id, id),
    CONSTRAINT doctor_user_fk FOREIGN KEY (clinic_id, user_id) REFERENCES app_user (clinic_id, id)
);

CREATE INDEX doctor_primary_specialty_idx ON doctor (primary_specialty_id);
