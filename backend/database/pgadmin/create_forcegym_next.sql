ALTER USER postgres WITH PASSWORD 'tilin';

CREATE DATABASE forcegym_next
  WITH OWNER = postgres
       ENCODING = 'UTF8'
       TEMPLATE = template0;

-- Despues de crear la base en pgAdmin, ejecuta sobre forcegym_next:
--   backend/database/postgresql/001_init.sql
--   backend/database/postgresql/002_seed.sql