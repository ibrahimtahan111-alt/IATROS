-- One-time local development setup. Run as a PostgreSQL superuser:
--
--   psql -U postgres -v app_password='choose-a-local-password' -f backend/db/local-setup.sql
--
-- Then set IATROS_DB_PASSWORD to the same value (see .env.example).

CREATE ROLE iatros WITH LOGIN PASSWORD :'app_password';

CREATE DATABASE iatros OWNER iatros ENCODING 'UTF8';
CREATE DATABASE iatros_test OWNER iatros ENCODING 'UTF8';
