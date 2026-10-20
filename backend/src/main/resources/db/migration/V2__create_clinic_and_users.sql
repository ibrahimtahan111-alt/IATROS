-- Clinics, clinic settings, and user accounts.
-- Every clinic-owned table exposes UNIQUE (clinic_id, id) so children can use composite
-- foreign keys that make cross-clinic references impossible at the database level.

CREATE TABLE clinic (
    id          uuid         PRIMARY KEY,
    name        varchar(200) NOT NULL CHECK (btrim(name) <> ''),
    email       citext       CHECK (length(email) <= 254),
    phone       varchar(40),
    address     varchar(500),
    status      varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    version     bigint       NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE clinic_settings (
    clinic_id                    uuid        PRIMARY KEY REFERENCES clinic (id),
    time_zone                    varchar(64) NOT NULL,
    default_appointment_minutes  smallint    NOT NULL DEFAULT 30 CHECK (default_appointment_minutes BETWEEN 5 AND 480),
    default_language             varchar(5)  NOT NULL DEFAULT 'en' CHECK (default_language IN ('en', 'fr')),
    week_start_day               smallint    NOT NULL DEFAULT 1 CHECK (week_start_day BETWEEN 1 AND 7),
    version                      bigint      NOT NULL DEFAULT 0,
    created_at                   timestamptz NOT NULL DEFAULT now(),
    updated_at                   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id                  uuid         PRIMARY KEY,
    clinic_id           uuid         NOT NULL REFERENCES clinic (id),
    first_name          varchar(100) NOT NULL CHECK (btrim(first_name) <> ''),
    last_name           varchar(100) NOT NULL CHECK (btrim(last_name) <> ''),
    email               citext       NOT NULL CHECK (length(email) BETWEEN 3 AND 254),
    phone               varchar(40),
    password_hash       varchar(255),
    role                varchar(20)  NOT NULL CHECK (role IN ('OWNER', 'DOCTOR', 'SECRETARY')),
    status              varchar(20)  NOT NULL DEFAULT 'INVITED' CHECK (status IN ('INVITED', 'ACTIVE', 'DEACTIVATED')),
    preferred_language  varchar(5)   CHECK (preferred_language IN ('en', 'fr')),
    last_login_at       timestamptz,
    deactivated_at      timestamptz,
    version             bigint       NOT NULL DEFAULT 0,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT app_user_email_uk UNIQUE (email),
    CONSTRAINT app_user_clinic_id_uk UNIQUE (clinic_id, id),
    CONSTRAINT app_user_active_requires_password CHECK (status <> 'ACTIVE' OR password_hash IS NOT NULL),
    CONSTRAINT app_user_deactivated_at_matches_status CHECK ((status = 'DEACTIVATED') = (deactivated_at IS NOT NULL))
);

CREATE INDEX app_user_clinic_name_idx ON app_user (clinic_id, last_name, first_name);
