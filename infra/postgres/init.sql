-- Runs once, on first startup of an empty postgres volume.
-- The visionone database is created by POSTGRES_DB; this adds Keycloak's own.
CREATE DATABASE keycloak OWNER visionone;
