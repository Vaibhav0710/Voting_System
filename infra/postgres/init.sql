-- Runs once, on the first start of an empty Postgres volume (docker-entrypoint-initdb.d).
-- One database per service; tables are created by each service (Hibernate ddl-auto).
CREATE DATABASE userservice_db;
CREATE DATABASE candidateservice_db;
CREATE DATABASE voting_service_db;
