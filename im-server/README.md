# im-server

Java service for enterprise IM.

## Current Capabilities

- Spring Boot HTTP API.
- Netty TCP Socket gateway.
- WebSocket gateway at `/ws/im`.
- Flyway DB schema for users, conversations, messages, admin roles, audit logs, and call records.
- `/actuator/health`.
- `/api/auth/login` demo user login.
- `/api/admin/auth/login` admin login.
- `/api/calls/config` and `/api/calls/readiness` for TURN/PJSIP call signaling checks.
- Admin APIs protected by admin token and role checks.
- TCP/WS JSON Line protocol: `AUTH`, `PING`, `TEXT`, `ACK`.

## Local Run

```powershell
mvn spring-boot:run
```

HTTP: `http://localhost:8080`

TCP: `localhost:9000`

Admin demo login:

```text
phone: 18800000000
password: admin123
```

## Docker

From `d:\work`:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Services:

- `im-server`: HTTP `18080`, TCP `19090` on the host; HTTP `8080`, TCP `9000` inside the container.
- `postgres`: `5432`
- `redis`: `6379`
- `minio`: API `9101`, console `9102`
- `coturn`: `3478/tcp`, `3478/udp`

The server reads DB, Redis, MinIO, TURN, and PJSIP addresses from environment variables in `.env`.

## Call Readiness

```powershell
Invoke-RestMethod http://localhost:8080/api/calls/readiness
```

Expected `data.ready=true` when TURN and PJSIP settings are present. The readiness API reports configuration state without returning the TURN password value.
