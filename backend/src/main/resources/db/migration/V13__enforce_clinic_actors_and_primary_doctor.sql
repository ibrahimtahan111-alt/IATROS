-- Integrity rules that application code already follows, enforced even when it does not:
-- every patient commit has one primary doctor, a doctor profile belongs to a DOCTOR or OWNER,
-- and clinical actors belong to the same clinic as the row they touch.

CREATE FUNCTION enforce_patient_primary_doctor() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    target_patient uuid;
BEGIN
    IF TG_TABLE_NAME = 'patient' THEN
        IF TG_OP = 'DELETE' THEN
            RETURN NULL;
        END IF;
        target_patient := NEW.id;
    ELSE
        target_patient := COALESCE(NEW.patient_id, OLD.patient_id);
        IF NOT EXISTS (SELECT 1 FROM patient WHERE id = target_patient) THEN
            RETURN NULL;
        END IF;
        IF TG_OP = 'UPDATE' AND OLD.patient_id IS DISTINCT FROM NEW.patient_id
            AND EXISTS (SELECT 1 FROM patient WHERE id = OLD.patient_id)
            AND NOT EXISTS (
                SELECT 1 FROM patient_doctor
                WHERE patient_id = OLD.patient_id AND relationship_type = 'PRIMARY'
            ) THEN
            RAISE EXCEPTION 'patient_requires_primary_doctor'
                USING ERRCODE = '23514', CONSTRAINT = 'patient_requires_primary_doctor';
        END IF;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM patient_doctor
        WHERE patient_id = target_patient AND relationship_type = 'PRIMARY'
    ) THEN
        RAISE EXCEPTION 'patient_requires_primary_doctor'
            USING ERRCODE = '23514', CONSTRAINT = 'patient_requires_primary_doctor';
    END IF;
    RETURN NULL;
END;
$$;

-- Deferred so the patient and its PRIMARY link can be inserted in one transaction.
CREATE CONSTRAINT TRIGGER patient_requires_primary_doctor
    AFTER INSERT OR UPDATE OR DELETE ON patient
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION enforce_patient_primary_doctor();

CREATE CONSTRAINT TRIGGER patient_doctor_requires_primary
    AFTER INSERT OR UPDATE OR DELETE ON patient_doctor
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION enforce_patient_primary_doctor();

CREATE FUNCTION doctor_user_must_practice() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    user_role    varchar(20);
    user_clinic  uuid;
BEGIN
    SELECT role, clinic_id INTO user_role, user_clinic
    FROM app_user
    WHERE id = NEW.user_id;

    -- A missing or cross-clinic user is rejected by doctor_user_fk.
    IF NOT FOUND OR user_clinic IS DISTINCT FROM NEW.clinic_id THEN
        RETURN NEW;
    END IF;
    IF user_role NOT IN ('DOCTOR', 'OWNER') THEN
        RAISE EXCEPTION 'doctor_user_role'
            USING ERRCODE = '23514', CONSTRAINT = 'doctor_user_role';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER doctor_user_role
    BEFORE INSERT OR UPDATE OF clinic_id, user_id ON doctor
    FOR EACH ROW EXECUTE FUNCTION doctor_user_must_practice();

CREATE FUNCTION app_user_role_keeps_doctor_profile() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF NEW.role IS DISTINCT FROM OLD.role
        AND NEW.role NOT IN ('DOCTOR', 'OWNER')
        AND EXISTS (SELECT 1 FROM doctor WHERE user_id = NEW.id) THEN
        RAISE EXCEPTION 'doctor_user_role'
            USING ERRCODE = '23514', CONSTRAINT = 'doctor_user_role';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER app_user_role_keeps_doctor_profile
    BEFORE UPDATE OF role ON app_user
    FOR EACH ROW EXECUTE FUNCTION app_user_role_keeps_doctor_profile();

-- Clinical entries gain clinic_id so actor foreign keys can use (clinic_id, user id),
-- the same shape appointment_created_by_fk already uses.
DO $$
DECLARE
    entry_table text;
    fk          record;
BEGIN
    FOREACH entry_table IN ARRAY ARRAY[
        'allergy', 'medical_condition', 'medication', 'medical_history_entry', 'medical_note'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN clinic_id uuid', entry_table);
        EXECUTE format(
            'UPDATE %I AS entry
                SET clinic_id = record.clinic_id
               FROM medical_record AS record
              WHERE record.id = entry.medical_record_id',
            entry_table);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN clinic_id SET NOT NULL', entry_table);

        FOR fk IN
            SELECT con.conname
            FROM pg_constraint con
            JOIN pg_class rel ON rel.oid = con.conrelid
            WHERE rel.relname = entry_table
              AND con.contype = 'f'
        LOOP
            EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', entry_table, fk.conname);
        END LOOP;

        EXECUTE format(
            'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (clinic_id, medical_record_id) REFERENCES medical_record (clinic_id, id)',
            entry_table, entry_table || '_record_fk');
        EXECUTE format(
            'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (clinic_id, created_by) REFERENCES app_user (clinic_id, id)',
            entry_table, entry_table || '_created_by_fk');
        EXECUTE format(
            'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (clinic_id, updated_by) REFERENCES app_user (clinic_id, id)',
            entry_table, entry_table || '_updated_by_fk');
        EXECUTE format(
            'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (clinic_id, deleted_by) REFERENCES app_user (clinic_id, id)',
            entry_table, entry_table || '_deleted_by_fk');
    END LOOP;
END $$;

ALTER TABLE schedule_exception ADD COLUMN clinic_id uuid;

UPDATE schedule_exception AS exception
   SET clinic_id = doctor.clinic_id
  FROM doctor
 WHERE doctor.id = exception.doctor_id;

ALTER TABLE schedule_exception ALTER COLUMN clinic_id SET NOT NULL;

ALTER TABLE schedule_exception DROP CONSTRAINT schedule_exception_doctor_id_fkey;
ALTER TABLE schedule_exception DROP CONSTRAINT schedule_exception_created_by_fkey;

ALTER TABLE schedule_exception
    ADD CONSTRAINT schedule_exception_doctor_fk
    FOREIGN KEY (clinic_id, doctor_id) REFERENCES doctor (clinic_id, id);

ALTER TABLE schedule_exception
    ADD CONSTRAINT schedule_exception_created_by_fk
    FOREIGN KEY (clinic_id, created_by) REFERENCES app_user (clinic_id, id);

-- When both are set, the actor must be a user of that clinic. Either column null skips the check
-- (system events, and events recorded before a clinic exists).
ALTER TABLE audit_event
    ADD CONSTRAINT audit_event_actor_clinic_fk
    FOREIGN KEY (clinic_id, actor_user_id) REFERENCES app_user (clinic_id, id);
