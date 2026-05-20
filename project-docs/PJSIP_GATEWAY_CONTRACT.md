# PJSIP Gateway Contract

Current status: the project now includes a runnable `pjsip-gateway` service and `im-server` calls it through `PJSIP_SIGNAL_URL`.

This completes the backend media-gateway boundary: create call, answer call, hang up call, store `pjsip_session_id`, `media_status`, and `media_error`.

The gateway now supports three engines:

- `simulated-pjsip`: fallback state engine; no native media.
- `external-pjsip-command`: drives real PJSIP control scripts through `PJSIP_CREATE_CMD`, `PJSIP_ANSWER_CMD`, and `PJSIP_HANGUP_CMD`.
- `pjsua-adapter`: starts a real `pjsua` process when `PJSUA_BIN` or a `pjsua` executable in `PATH` is available.

True RTP media is complete only when `/health` reports `external-pjsip-command` or `pjsua-adapter`, and client devices are registered/connected to the same SIP/PJSIP media path.

## Base URL

Docker Compose:

```text
PJSIP_SIGNAL_URL=http://pjsip-gateway:7070
```

Local Windows run:

```text
PJSIP_SIGNAL_URL=http://127.0.0.1:7070
```

## Endpoints

### Health

```http
GET /health
```

Example response:

```json
{
  "status": "UP",
  "engine": "simulated-pjsip",
  "engineHealth": {
    "available": true
  },
  "sessions": 0
}
```

### Create Media Session

```http
POST /api/pjsip/calls
Content-Type: application/json
```

```json
{
  "callId": "call_xxx",
  "callerId": "u_a",
  "calleeId": "u_b",
  "conversationId": "c_a_b",
  "mediaType": "audio",
  "turnSessionId": "turn_xxx"
}
```

Expected response:

```json
{
  "sessionId": "pjsip_xxx",
  "callId": "call_xxx",
  "status": "ringing",
  "engine": "simulated-pjsip",
  "mediaType": "audio",
  "turnSessionId": "turn_xxx",
  "processPid": null,
  "error": null
}
```

When a native engine is attached, `engine` becomes `pjsua-adapter` or `external-pjsip-command`, and `processPid` is populated when the gateway starts a process.

## Native Engine Environment

`pjsua-adapter`:

- `PJSUA_BIN`: path or command name for `pjsua`.
- `PJSIP_SIP_DOMAIN`: SIP domain used for `sip:{userId}@domain`.
- `PJSIP_REGISTRAR`: optional SIP registrar URI.
- `PJSIP_REALM`: optional auth realm, default `*`.
- `PJSIP_USERNAME`: optional SIP auth username.
- `PJSIP_PASSWORD`: optional SIP auth password.
- `PJSUA_EXTRA_ARGS`: extra args passed to `pjsua`.
- `PJSIP_TARGET_TEMPLATE`: optional target URI template, default `sip:${calleeId}@${sipDomain}`.

`external-pjsip-command`:

- `PJSIP_CREATE_CMD`: command template required to create a native call.
- `PJSIP_ANSWER_CMD`: optional command template to answer a native call.
- `PJSIP_HANGUP_CMD`: optional command template to hang up a native call.

Available template variables: `callId`, `sessionId`, `callerId`, `calleeId`, `conversationId`, `mediaType`, `turnSessionId`, `sipDomain`.

### Answer Media Session

```http
POST /api/pjsip/calls/{callId}/answer
Content-Type: application/json
```

```json
{
  "actorId": "u_b"
}
```

### Hang Up Media Session

```http
POST /api/pjsip/calls/{callId}/hangup
Content-Type: application/json
```

```json
{
  "actorId": "u_a"
}
```

### List Sessions

```http
GET /api/pjsip/sessions
```

## Server Behavior

- If PJSIP gateway is reachable, `call_records.media_status = media_ready`.
- If PJSIP gateway is missing or unreachable, call signaling still works and `call_records.media_status = signaling_only`.
- `pjsip_session_id` stores gateway session id.
- `media_error` stores gateway error for admin diagnosis.

## Run

Local:

```powershell
.\scripts\run-pjsip-gateway.ps1
```

Docker:

```powershell
docker compose up -d pjsip-gateway
```

## Native PJSIP Remaining Work

The REST boundary is done and runnable. For true media, install or deploy a native PJSIP engine and set:

```text
PJSUA_BIN=/path/to/pjsua
```

Then replace/extend the gateway engine implementation to create actual SIP/RTP sessions while preserving the same REST API.
