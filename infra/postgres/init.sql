-- Runs once, on the first start of an empty Postgres volume (docker-entrypoint-initdb.d, via psql).
-- One database for the voting system, one schema per service; tables are created by each service (Hibernate ddl-auto).
CREATE DATABASE voting_system;
\connect voting_system
CREATE SCHEMA user_service;
CREATE SCHEMA candidate_service;
CREATE SCHEMA voting_service;
