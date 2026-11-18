-- Crea el usuario y la base del demo. Se ejecuta UNA vez, como superusuario (postgres), en pgAdmin
-- (Query Tool sobre la base "postgres") o con:
--   psql -U postgres -f db/crear-base.sql
--
-- Cambia la contraseña antes de ejecutarlo; es la que después va en DB_PASSWORD.
-- Las tablas NO se crean aquí: las crea el backend al arrancar (Flyway, src/main/resources/db/migration).

CREATE ROLE smp_demo LOGIN PASSWORD 'cambia-esta-clave';
CREATE DATABASE smp_demo OWNER smp_demo;
