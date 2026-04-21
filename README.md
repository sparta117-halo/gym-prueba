# Force Gym

La interfaz principal del proyecto ahora vive en `frontend/` y usa Next.js como PWA instalable. El backend Spring Boot modular permanece en `backend/`.

## Nuevo frontend web

- Framework: Next.js App Router
- Modo instalable: PWA con manifest y service worker
- Offline: cache de la app, IndexedDB local y cola de operaciones pendientes
- Sincronizacion: cuando vuelve la conectividad, empuja cambios a `service-membresia` y luego refresca los datos desde el backend

## Como correr el frontend localmente

1. Inicia el backend en `backend/`.
2. En otra terminal entra a `frontend/`.
3. Instala dependencias:

```bash
npm install
```

4. Inicia el entorno de desarrollo:

```bash
npm run dev
```

Si quieres abrirla desde otro dispositivo en tu misma red local:

```bash
npm run dev:lan
```

La app queda en `http://localhost:3000` y por defecto reescribe `/api/*` hacia `http://localhost:8080/api/*`.

El usuario no necesita escribir la API base en la pantalla de acceso: la web usa esa ruta automaticamente.

Si necesitas otra URL de backend, define `FORCE_GYM_API_BASE` en `frontend/.env.local`, por ejemplo:

```bash
FORCE_GYM_API_BASE=https://tu-dominio/api
NEXT_PUBLIC_FORCE_GYM_API_BASE=https://tu-dominio/api
```

## Como generar la PWA

```bash
cd frontend
npm run build
npm run start
```

Para exponer la build a otros dispositivos en la LAN:

```bash
cd frontend
npm run build
npm run start:lan
```

La web puede instalarse desde el navegador como si fuera una app, ejecutarse a pantalla completa y seguir funcionando con la informacion local aun sin internet.

Para abrirla desde otro telefono, tablet o laptop en la misma red, usa `http://IP-DE-TU-PC:3000`.

Importante: para que el navegador permita instalacion PWA completa y service worker en otro dispositivo, normalmente necesitas HTTPS. En LAN por HTTP puedes probar la web y compartir la misma base de datos, pero la instalacion como app puede quedar limitada por seguridad del navegador.

## Comandos rapidos desde la raiz

Desde la raiz del proyecto quedaron estos comandos PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-all.ps1
powershell -ExecutionPolicy Bypass -File .\stop-all.ps1
powershell -ExecutionPolicy Bypass -File .\restart-all.ps1
```

Estos comandos:

- levantan backend y frontend desde una sola entrada
- dejan el frontend en modo productivo para que la instalacion como app funcione mejor
- levantan tambien el proxy HTTPS local para pruebas de instalacion en movil
- evitan abrir una ventana visible por cada microservicio
- guardan logs temporales en `.runtime/` y `backend/.runtime/`

Si prefieres correrlo desde `frontend/`, quedaron tambien estos scripts npm:

```powershell
cd frontend
npm run web
npm run web:stop
npm run web:restart
```

Tambien quedaron accesos directos para doble clic en Windows:

- `iniciar-force-gym.bat`
- `detener-force-gym.bat`
- `reiniciar-force-gym.bat`

Con el arranque unificado quedan estas rutas:

- `http://localhost:3000`
- `https://localhost:3443`

Para instalar desde otro dispositivo en la misma red, usa:

- `https://IP-DE-TU-PC:3443`

El arranque tambien genera `frontend/public/lan-access.json` con la IP local detectada para que el boton `Instalar` pueda redirigir al enlace HTTPS correcto cuando haga falta.

## HTTPS local para pruebas PWA

En `frontend/` queda configurado un proxy HTTPS local usando `local-ssl-proxy`.

1. En una terminal inicia la web en LAN:

```bash
cd frontend
npm run dev:lan
```

2. En otra terminal inicia el proxy HTTPS:

```bash
cd frontend
npm run proxy:https
```

3. Desde otro dispositivo en la misma red abre:

```text
https://IP-DE-TU-PC:3443
```

El certificado es autofirmado, asi que para pruebas locales debes aceptarlo manualmente en el navegador.

Si quieres validar la build productiva con HTTPS en LAN:

```bash
cd frontend
npm run build
npm run start:lan
```

En otra terminal:

```bash
cd frontend
npm run proxy:https
```

## Levantar todo con Docker

Desde la raiz del repo:

```bash
docker compose up --build
```

Esto levanta PostgreSQL, los microservicios Spring Boot y el frontend Next en `http://localhost:3000`.

La web usa `/api/*` hacia `service-gateway`, por lo que en Compose no necesitas cambiar la URL base del navegador.

Si otro dispositivo entra a `http://IP-DE-TU-PC:3000`, usara el mismo backend y la misma base `forcegym_next`, por lo que todos veran y grabaran sobre los mismos datos.

## Base de datos

El backend ya no apunta por defecto a `forcegym`, sino a `forcegym_next` con clave `tilin`.

- Base: `forcegym_next`
- Usuario: `postgres`
- Clave: `tilin`

Si administras PostgreSQL desde pgAdmin, hay un apoyo en `backend/database/pgadmin/create_forcegym_next.sql`.

Tambien puedes importar `backend/database/pgadmin/servers.json` para precargar el servidor local con host `127.0.0.1`, puerto `5432`, usuario `postgres` y SSL `prefer`.

Pasos rapidos en pgAdmin:

1. Abre pgAdmin y crea o importa un servidor nuevo.
2. En `Host name/address` usa `127.0.0.1`.
3. En `Port` usa `5432`.
4. En `Maintenance database` usa `postgres`.
5. En `Username` usa `postgres`.
6. En `Password` usa `tilin`.
7. Refresca `Databases` y deberias ver `forcegym_next` cuando Docker Compose este levantado.

Si no aparece, primero levanta PostgreSQL con:

```bash
docker compose up --build postgres
```

o bien todo el stack con:

```bash
docker compose up --build
```

Nota: desde este repositorio no se puede registrar automaticamente la conexion dentro de tu instalacion local de pgAdmin; lo que si queda listo es el archivo `servers.json` con los datos correctos y el SQL auxiliar para crear o revisar la base.

## Validacion realizada

- Frontend: `npm run build` en `frontend/`
- Tipado: sin errores en `dashboard-shell.tsx`, `globals.css` y `README.md`
- Compose: PostgreSQL y `service-rutinas` ya apuntan a `forcegym_next` con usuario `postgres` y clave `tilin`
- pgAdmin: quedaron `backend/database/pgadmin/create_forcegym_next.sql` y `backend/database/pgadmin/servers.json` para conexion manual/importable

## Backend

La configuracion y los puertos del backend estan en [backend/README.md](backend/README.md).
