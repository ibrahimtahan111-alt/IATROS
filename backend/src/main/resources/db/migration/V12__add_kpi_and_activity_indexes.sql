-- Clinic-wide KPI counts of consultations in a date range (api.md §23).
CREATE INDEX consultation_clinic_time_idx ON consultation (clinic_id, consulted_at);

-- A doctor's activity feed: events that reference the doctor in their metadata (api.md §24, decisions.md D-039).
CREATE INDEX audit_event_doctor_ref_idx ON audit_event ((metadata ->> 'doctorId'), occurred_at DESC);
