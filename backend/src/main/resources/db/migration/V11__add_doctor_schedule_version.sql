-- Optimistic locking for a doctor's weekly schedule, which is saved as a whole (decisions.md D-035).
-- Kept separate from doctor.version so editing the profile and the schedule at the same time do not conflict.

ALTER TABLE doctor ADD COLUMN schedule_version bigint NOT NULL DEFAULT 0;
