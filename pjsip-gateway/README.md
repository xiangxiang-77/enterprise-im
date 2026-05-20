# PJSIP Gateway

Minimal media gateway service for the Enterprise IM delivery.

It implements the REST contract consumed by `im-server`:

- `POST /api/pjsip/calls`
- `POST /api/pjsip/calls/{callId}/answer`
- `POST /api/pjsip/calls/{callId}/hangup`
- `GET /health`
- `GET /api/pjsip/sessions`

## Engine Modes

- `simulated-pjsip`: fallback, keeps media session state so backend can complete the PJSIP gateway loop.
- `external-pjsip-command`: selected when `PJSIP_CREATE_CMD` is configured. Use this to drive an installed PJSIP daemon, PBX, or native control script.
- `pjsua-adapter`: selected when `PJSUA_BIN` points to an installed PJSIP `pjsua` binary, or when `pjsua` is available in `PATH`.

The Docker image builds and bundles PJSIP/pjsua 2.17 at `/opt/pjsip/bin/pjsua`, so Compose defaults to `pjsua-adapter`. Host-side runs can still use a local `pjsua` binary or command templates.

Native RTP audio/video depends on the SIP/media topology used by the clients and deployment. This service makes the server-side PJSIP boundary runnable and testable inside Compose, with a real adapter path instead of only in-memory state.

## Native PJSIP Configuration

### Option A: bundled Docker pjsua

From the workspace root:

```powershell
docker compose build pjsip-gateway
docker compose up -d pjsip-gateway
curl http://127.0.0.1:7070/health
```

The response should include:

```json
{"engine":"pjsua-adapter"}
```

### Option B: host pjsua binary

Mount or install `pjsua`, then set:

```powershell
$env:PJSUA_BIN="C:\path\to\pjsua.exe"
$env:PJSIP_SIP_DOMAIN="sip.example.local"
$env:PJSIP_REGISTRAR="sip:sip.example.local"
$env:PJSIP_USERNAME="alice"
$env:PJSIP_PASSWORD="secret"
$env:PJSUA_EXTRA_ARGS="--auto-answer=200"
python app.py
```

Docker Compose can use the same env vars and mount a different binary into the container if needed.

### Option C: command templates

If a real PJSIP service is controlled by scripts, set command templates:

```powershell
$env:PJSIP_CREATE_CMD="pjsip-create-call --call ${callId} --from ${callerId} --to ${calleeId}"
$env:PJSIP_ANSWER_CMD="pjsip-answer-call --call ${callId}"
$env:PJSIP_HANGUP_CMD="pjsip-hangup-call --call ${callId}"
python app.py
```

Template variables include `callId`, `sessionId`, `callerId`, `calleeId`, `conversationId`, `mediaType`, `turnSessionId`, and `sipDomain`.

Real readiness check:

```powershell
curl http://127.0.0.1:7070/health
```

`engine` should be `pjsua-adapter` or `external-pjsip-command`; `simulated-pjsip` means no native media engine is attached.

## Run

```powershell
python app.py
```

or through Docker Compose from the workspace root:

```powershell
docker compose up -d pjsip-gateway
```
