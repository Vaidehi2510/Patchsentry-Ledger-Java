-- Full portal-native PR/build/release snapshots, independently of legacy PR scoring.
CREATE TABLE assessment_archive (
 project_id uuid NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
 record_id varchar(64) NOT NULL,payload_digest varchar(64) NOT NULL,
 identity_digest varchar(64) NOT NULL,repository varchar(201) NOT NULL,
 revision varchar(64) NOT NULL,payload bytea NOT NULL,received_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(project_id,record_id,payload_digest)
);
CREATE OR REPLACE FUNCTION immutable_assessment_archive() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='UPDATE' OR EXISTS(SELECT 1 FROM projects WHERE id=OLD.project_id) THEN
 RAISE EXCEPTION 'Assessment archives are append-only'; END IF;RETURN OLD;END $$;
CREATE TRIGGER immutable_assessment_archive BEFORE UPDATE OR DELETE ON assessment_archive FOR EACH ROW EXECUTE FUNCTION immutable_assessment_archive();
