# Backend Force Gym

Backend Java modular para la PWA offline-first.

## Servicios

- `service-gateway`: punto unico de entrada para el frontend.
- `service-config`: configuracion centralizada para servicios y clientes.
- `service-membresia`: miembros, planes, pagos, facturacion y vencimientos.
- `service-rutinas`: rutinas, asignaciones y seguimiento.
- `service-media`: metadatos de fotos y archivos.
- `service-scheluder`: tareas programadas, sincronizacion y alertas.

## Stack base

- Java 21
- Spring Boot 3.4.x
- Spring Cloud 2024.x
- Maven multi-modulo

## Puertos iniciales

- `service-gateway`: `8080`
- `service-config`: `8081`
- `service-membresia`: `8082`
- `service-rutinas`: `8083`
- `service-media`: `8084`
- `service-scheluder`: `8085`

## Base de datos local

- Motor: PostgreSQL local
- Base de datos: `forcegym_next`
- Usuario: `postgres`
- Clave: `tilin`

## Ejecucion local

1. Inicia PostgreSQL local o levanta `docker-compose.yml` si tienes Docker.
2. Arranca los servicios principales:
	- `./mvnw.cmd -pl service-config spring-boot:run`
	- `./mvnw.cmd -pl service-membresia spring-boot:run`
	- `./mvnw.cmd -pl service-gateway spring-boot:run`
3. Verifica salud:
	- `http://localhost:8081/actuator/health`
	- `http://localhost:8082/actuator/health`
	- `http://localhost:8080/actuator/health`

### Scripts de ciclo de vida en Windows

Desde `backend/` puedes usar:

- `powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1`
- `powershell -ExecutionPolicy Bypass -File .\scripts\stop-backend.ps1`
- `powershell -ExecutionPolicy Bypass -File .\scripts\restart-backend.ps1`

Los scripts:

- levantan `service-config`, `service-membresia`, `service-rutinas`, `service-media`, `service-scheluder` y `service-gateway`
- esperan health checks en `actuator/health`
- guardan PIDs en `backend/.runtime/backend-processes.json`
- dejan logs en `backend/.runtime/logs/`
- ejecutan los procesos en background sin abrir una ventana visible por cada servicio

## Ejecucion con Docker Compose

Desde la raiz del workspace puedes levantar PostgreSQL, backend y frontend con:

```bash
docker compose up --build
```

El compose principal usa la base `forcegym_next`, publica gateway en `8080` y la web en `3000`.

## Variables para hosting

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SERVICE_CONFIG_URL`
- `SERVICE_MEMBRESIA_URL`
- `SERVICE_RUTINAS_URL`
- `SERVICE_MEDIA_URL`
- `SERVICE_SCHELUDER_URL`
- `FORCE_GYM_JWT_SECRET`
- `CONFIG_REPO_LOCATION`

Con estas variables puedes usar el mismo backend en local y en un host sin dejar rutas o credenciales fijas en `localhost`.

## pgAdmin

Si vas a administrar la instancia desde pgAdmin, crea o reconfigura el servidor PostgreSQL con:

- Host: `localhost`
- Puerto: `5432`
- Usuario: `postgres`
- Clave: `tilin`

Y usa la base `forcegym_next`. Hay un script de apoyo en `database/pgadmin/create_forcegym_next.sql`.

Si prefieres evitar configurarlo a mano, importa `database/pgadmin/servers.json` desde pgAdmin y luego asigna la clave `tilin` al guardar la conexion.

## Credenciales bootstrap

- Admin gateway: `admin` / `admin`
- Miembro demo: `ana.lopez` / `fit123`

## Siguientes pasos previstos

1. Incorporar seguridad JWT y refresh token en `service-gateway`.
2. Agregar PostgreSQL y Flyway por servicio transaccional.
3. Publicar contratos OpenAPI por modulo.
4. Implementar endpoints de bootstrap y sincronizacion incremental.
5. Añadir auditoria, observabilidad y colas de reintento.